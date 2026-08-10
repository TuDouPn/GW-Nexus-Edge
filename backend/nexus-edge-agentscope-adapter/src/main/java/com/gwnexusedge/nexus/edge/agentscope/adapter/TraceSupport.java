package com.gwnexusedge.nexus.edge.agentscope.adapter;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.reactor.v3_1.ContextPropagationOperator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AgentScope Adapter 的 OpenTelemetry Trace 支持（P0-1 修复）。
 *
 * <p>并发隔离：每个 Task 创建独立的 OTel span 与 Trace ID；span 通过
 * {@link ContextPropagationOperator#storeOpenTelemetryContext} 写入 Reactor Context，
 * 由 AgentScope core 已注册的 ContextPropagationOperator 在异步执行链中传播
 * （不依赖调用线程的 makeCurrent，避免上下文泄漏与并发污染）。
 *
 * <p>span 生命周期：由调用方在 {@code doFinally} 中 {@link #end(TraceHandle)} 结束
 * （覆盖完成/错误/取消），保证每个 Task 的 span 独立结束。
 *
 * <p>边界：未配置 OTel SDK 时（no-op provider）traceId 为全零，由测试与生产门禁拒绝。
 */
public final class TraceSupport {

    private TraceSupport() {
        // 工具类，禁止实例化
    }

    private static final Map<String, Span> ACTIVE_SPANS = new ConcurrentHashMap<>();

    /**
     * 为一次执行创建独立 span 并返回 Trace 句柄。
     *
     * <p>span 创建后即确定 traceId；span 上下文存入 Reactor Context 供异步链传播。
     *
     * @return Trace 句柄（span + traceId + 已注入 OTel Context 的 Reactor Context）
     */
    public static TraceHandle start() {
        Span span = GlobalOpenTelemetry.getTracer("com.gwnexusedge.nexus.edge.agentscope")
                .spanBuilder("nexus-edge.agent.execution")
                .startSpan();
        SpanContext sc = span.getSpanContext();
        String traceId = sc.getTraceId();

        // 把 OTel Context 写入 Reactor Context（不 makeCurrent 于调用线程，避免泄漏）。
        reactor.util.context.Context reactorCtx =
                ContextPropagationOperator.storeOpenTelemetryContext(
                        reactor.util.context.Context.empty(),
                        Context.current().with(span));
        return new TraceHandle(span, traceId, reactorCtx);
    }

    /** 结束 span（幂等；重复调用安全）。 */
    public static void end(TraceHandle handle) {
        if (handle != null) {
            handle.span.end();
            ACTIVE_SPANS.remove(handle.spanId());
        }
    }

    /** 是否真实 Trace ID（32 位 hex，非全零、非 unassigned）。 */
    public static boolean isRealTraceId(String traceId) {
        return traceId != null
                && traceId.matches("[0-9a-f]{32}")
                && !traceId.matches("0{32}")
                && !"unassigned".equals(traceId);
    }

    /** 一次执行的 Trace 句柄。 */
    public record TraceHandle(Span span, String traceId, reactor.util.context.Context reactorContext) {
        public String spanId() {
            return span.getSpanContext().getSpanId();
        }
    }
}
