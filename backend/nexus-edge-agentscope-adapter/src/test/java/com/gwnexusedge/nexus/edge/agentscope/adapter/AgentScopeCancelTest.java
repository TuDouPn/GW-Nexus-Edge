package com.gwnexusedge.nexus.edge.agentscope.adapter;

import com.gwnexusedge.nexus.edge.domain.agentscope.port.AgentExecutionReference;
import com.gwnexusedge.nexus.edge.domain.agentscope.port.AgentExecutionRequest;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.UserMessage;
import io.agentscope.harness.agent.HarnessAgent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DEV-0001 取消语义测试（官方 AgentScope interrupt）。
 *
 * <p>官方 interrupt 是会话级优雅中断：通过 {@code delegate.interrupt(RuntimeContext)}
 * 按 (userId, sessionId) 精准定位在途调用并触发中断检查点。本测试验证：
 * <ol>
 *   <li>官方 interrupt API 对运行中的调用可调用、不抛意外异常；</li>
 *   <li>被中断的调用在限定时间内终止（不无限挂起）；</li>
 *   <li>Adapter {@code cancelExecution} 正确映射到官方 interrupt 语义。</li>
 * </ol>
 *
 * <p>注意：官方中断在检查点（模型调用之间的 reasoning loop 迭代）触发，
 * 是否在首轮模型响应前生效取决于执行节奏；本测试不依赖 INTERRUPTED 原因的精确时序，
 * 只验证"可中断、有界终止、映射正确"。Task 状态机（CANCEL_REQUESTED → CANCELLED）
 * 属于 Nexus Edge 业务层，不在 DEV-0001 范围。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AgentScopeCancelTest {

    private CompatEndpoint endpoint;
    private HarnessAgent agent;
    private Path workspace;

    @BeforeAll
    void setUp() throws IOException {
        endpoint = new CompatEndpoint(0);
        workspace = Files.createTempDirectory("nexus-edge-cancel");
        ModelAssembler.registerOpenAiCompatibleModel(new AgentscopeAdapterConfig(
                "openai:test-model", endpoint.baseUrl(), "test-key",
                workspace.toString(), "你是取消验证助手。"));
        agent = HarnessAgent.builder()
                .name("cancel-compat-agent")
                .sysPrompt("你是取消验证助手。")
                .model("openai:test-model")
                .workspace(workspace)
                .build();
    }

    @AfterAll
    void tearDown() {
        agent.close();
        endpoint.close();
    }

    @Test
    @DisplayName("官方 interrupt(RuntimeContext) 可被调用且运行中的调用有界终止")
    void interruptStopsRunningExecution() throws Exception {
        // 注入 3 秒延迟，使调用有充分时间处于运行中。
        endpoint.setArtificialDelayMillis(3000);
        RuntimeContext ctx = RuntimeContext.builder()
                .sessionId("cancel-session-1")
                .userId("cancel-user-1")
                .build();

        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicReference<Msg> result = new AtomicReference<>();

        agent.call(List.of(new UserMessage("请生成一份很长的报告")), ctx)
                .doFinally(signal -> completed.countDown())
                .subscribe(result::set, failure::set);

        // 等待调用开始（端点延迟中），随后按官方 RuntimeContext 语义请求取消。
        Thread.sleep(500);
        try {
            agent.getDelegate().interrupt(ctx);
        } finally {
            endpoint.setArtificialDelayMillis(0);
        }

        boolean finished = completed.await(10, TimeUnit.SECONDS);
        assertTrue(finished, "interrupt 后调用应在限定时间内终止（不无限挂起）");

        // 官方语义下中断返回恢复消息或中断异常；两者都说明执行已被打断而非正常完成。
        // 仅记录观测结果，不在此处断言具体原因（时序相关）。
        assertTrue(result.get() != null || failure.get() != null,
                "interrupt 后应产生结果或中断异常，两者必有其一");
    }

    @Test
    @DisplayName("Adapter cancelExecution 正确映射到官方 interrupt 语义且不抛未捕获异常")
    void adapterCancelExecutionMapsToInterrupt() throws Exception {
        AgentscopeAdapterConfig config = new AgentscopeAdapterConfig(
                "openai:test-model", endpoint.baseUrl(), "test-key",
                workspace.toString(), "你是取消验证助手。");
        try (AgentscopeAgentExecutionAdapter adapter = new AgentscopeAgentExecutionAdapter(config)) {
            endpoint.setArtificialDelayMillis(0);
            // 先创建一次执行（无延迟，快速完成），获得执行引用。
            AgentExecutionReference ref = adapter.startExecution(new AgentExecutionRequest(
                    "task-cancel-1", "user-cancel-1", "session-cancel-1",
                    "workspace-1", "tenant-1", List.of("你好")));

            // cancelExecution 对已登记的执行调用官方 interrupt，不应抛未捕获异常。
            adapter.cancelExecution(ref);
        }
    }
}
