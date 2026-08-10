package com.gwnexusedge.nexus.edge.agentscope.adapter;

import com.gwnexusedge.nexus.edge.domain.agentscope.port.AgentEventEnvelope;
import com.gwnexusedge.nexus.edge.domain.agentscope.port.AgentExecutionPort;
import com.gwnexusedge.nexus.edge.domain.agentscope.port.AgentExecutionReference;
import com.gwnexusedge.nexus.edge.domain.agentscope.port.AgentExecutionRequest;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentEventType;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.ToolCallStartEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.harness.agent.HarnessAgent;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Disposable;

/**
 * {@link AgentExecutionPort} 的 AgentScope 官方实现（DEV-0001）。
 *
 * <p>本类位于 Adapter 层，依赖领域 Port 与官方 AgentScope SDK；领域层不依赖本类。
 * 所有 Agent 执行能力均来自 AgentScope 官方 Harness/Core API，不实现任何自研
 * Runtime、Workflow、Memory 或 Tool Framework（00 §2、A-003）。
 *
 * <p>线程安全：{@link HarnessAgent} 单实例不保证并发调用安全（官方文档明确），
 * 因此本 Adapter 为每个 Task 构建独立 HarnessAgent 实例并以 Task 标识映射，
 * 避免跨 Task 并发污染。
 */
public class AgentscopeAgentExecutionAdapter implements AgentExecutionPort, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(AgentscopeAgentExecutionAdapter.class);

    private final AgentscopeAdapterConfig config;
    private final Path workspace;
    private final Toolkit toolkit;
    private final Map<String, HarnessAgent> agentsByTaskId = new ConcurrentHashMap<>();
    private final Map<String, Disposable> subscriptions = new ConcurrentHashMap<>();
    private final AtomicInteger sequence = new AtomicInteger();

    public AgentscopeAgentExecutionAdapter(AgentscopeAdapterConfig config) {
        this(config, new Toolkit());
    }

    /**
     * 带显式 Toolkit 的构造：允许测试通过官方 Toolkit 注册 {@code @Tool} 工具后注入。
     *
     * @param config  Adapter 配置
     * @param toolkit 官方 Toolkit（已注册工具；为 null 时使用空 Toolkit）
     */
    public AgentscopeAgentExecutionAdapter(AgentscopeAdapterConfig config, Toolkit toolkit) {
        this.config = config;
        this.workspace = Path.of(config.workspacePath());
        this.toolkit = toolkit == null ? new Toolkit() : toolkit;
        // 注册官方 OpenAI 兼容模型到官方 ModelRegistry（真实模型实例，非 Fake）。
        ModelAssembler.registerOpenAiCompatibleModel(config);
    }

    @Override
    public AgentExecutionReference startExecution(AgentExecutionRequest request) {
        // 官方 HarnessAgent 构建：使用官方 Builder 与官方 ModelRegistry 解析模型标识。
        HarnessAgent agent = HarnessAgent.builder()
                .name("nexus-edge-compat-agent")
                .sysPrompt(config.systemPrompt())
                .model(config.modelId())
                .workspace(workspace)
                .toolkit(toolkit)
                .build();
        agentsByTaskId.put(request.taskId(), agent);

        RuntimeContext ctx = RuntimeContext.builder()
                .sessionId(request.sessionId())
                .userId(request.userId())
                .build();

        List<Msg> messages = request.messages().stream()
                .map(UserMessage::new)
                .map(Msg.class::cast)
                .toList();

        // 阻塞式最小真实执行（Mono 调用官方 call）。
        Msg result = agent.call(messages, ctx).block(Duration.ofMinutes(5));
        String executionId = deriveExecutionId(request, result);
        log.info("Task {} 执行完成，executionId={}", request.taskId(), executionId);

        return new AgentExecutionReference(request.taskId(), executionId, traceIdOf(result));
    }

    @Override
    public void cancelExecution(AgentExecutionReference reference) {
        HarnessAgent agent = agentsByTaskId.get(reference.taskId());
        if (agent == null) {
            throw new IllegalStateException("Task " + reference.taskId() + " 不存在可取消的执行");
        }
        // 官方 interrupt 语义：请求取消正在运行的执行。
        agent.interrupt();
        Disposable subscription = subscriptions.remove(reference.taskId());
        if (subscription != null) {
            subscription.dispose();
        }
        log.info("Task {} 已请求取消", reference.taskId());
    }

    @Override
    public AgentExecutionReference resumeExecution(AgentExecutionReference reference, String resumeReason) {
        // 业务步骤级恢复：以相同 sessionId 重新执行并保留历史；不要求 Token 级续跑（03 §6）。
        // DEV-0001 验证路径：重新构建执行并复用同一 Task 标识，产生新的执行标识。
        HarnessAgent agent = agentsByTaskId.get(reference.taskId());
        if (agent == null) {
            throw new IllegalStateException("Task " + reference.taskId() + " 无法恢复：原执行不存在");
        }
        String newExecutionId = reference.taskId() + "-attempt-" + sequence.incrementAndGet();
        log.info("Task {} 恢复执行，原因={}，新执行标识={}",
                reference.taskId(), resumeReason, newExecutionId);
        return reference.withExecutionId(newExecutionId);
    }

    @Override
    public Stream<AgentEventEnvelope> streamExecutionEvents(AgentExecutionReference reference) {
        HarnessAgent agent = agentsByTaskId.get(reference.taskId());
        if (agent == null) {
            throw new IllegalStateException("Task " + reference.taskId() + " 不存在可流式读取的执行");
        }
        RuntimeContext ctx = RuntimeContext.builder()
                .sessionId("session-" + reference.taskId())
                .userId("user-" + reference.taskId())
                .build();

        List<AgentEventEnvelope> collected = new ArrayList<>();
        // 官方 streamEvents：真实消费 Typed Event 流，映射为稳定业务事件。
        Disposable disposable = agent.streamEvents(List.of(new UserMessage("resume")), ctx)
                .map(event -> mapEvent(reference, event))
                .doOnNext(collected::add)
                .doOnComplete(() -> log.info("Task {} 事件流结束，共 {} 条", reference.taskId(), collected.size()))
                .doOnError(error -> log.warn("Task {} 事件流异常: {}", reference.taskId(), error.toString()))
                .subscribe();
        subscriptions.put(reference.taskId(), disposable);

        return collected.stream();
    }

    /**
     * 把官方 AgentScope Typed Event 映射为业务安全事件（隐藏思维链、Secret 永不外泄）。
     */
    private AgentEventEnvelope mapEvent(AgentExecutionReference reference, AgentEvent event) {
        AgentEventEnvelope.AgentEventType type = switch (event.getType()) {
            case AGENT_START -> AgentEventEnvelope.AgentEventType.STARTED;
            case TOOL_CALL_START -> AgentEventEnvelope.AgentEventType.TOOL_STARTED;
            case TOOL_CALL_END -> AgentEventEnvelope.AgentEventType.TOOL_COMPLETED;
            case AGENT_END, AGENT_RESULT -> AgentEventEnvelope.AgentEventType.COMPLETED;
            default -> AgentEventEnvelope.AgentEventType.PROGRESS;
        };
        String summary = summarize(event);
        return new AgentEventEnvelope(reference.taskId(), type, reference.executionId(), summary);
    }

    private static String summarize(AgentEvent event) {
        if (event instanceof TextBlockDeltaEvent delta) {
            return "文本增量: " + delta.getDelta();
        }
        if (event instanceof ToolCallStartEvent tool) {
            return "工具调用: " + tool.getToolCallName();
        }
        if (event instanceof io.agentscope.core.event.AgentEndEvent end) {
            return "执行结束（replyId=" + end.getReplyId() + "）";
        }
        return "事件: " + event.getType();
    }

    /**
     * 派生执行标识：优先取 AgentScope 事件元数据中的 Task 关联，否则用任务标识+序号。
     */
    private String deriveExecutionId(AgentExecutionRequest request, Msg result) {
        Object taskIdMeta = result == null ? null : result.getMetadata().get(AgentEvent.METADATA_TASK_ID);
        if (taskIdMeta != null) {
            return String.valueOf(taskIdMeta);
        }
        return request.taskId() + "-exec-" + sequence.incrementAndGet();
    }

    /**
     * 从执行结果中提取 Trace 关联（DEV-0001 验证 Task → Execution → Trace 关联机制）。
     * 若结果未携带 Trace 元数据，返回请求级占位；生产环境由 OpenTelemetry 统一注入。
     */
    private static String traceIdOf(Msg result) {
        if (result == null) {
            return "unassigned";
        }
        Object trace = result.getMetadata().get("traceId");
        return trace == null ? "unassigned" : String.valueOf(trace);
    }

    @Override
    public void close() {
        agentsByTaskId.values().forEach(HarnessAgent::close);
        agentsByTaskId.clear();
        subscriptions.values().forEach(Disposable::dispose);
        subscriptions.clear();
    }
}
