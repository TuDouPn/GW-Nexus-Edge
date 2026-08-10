package com.gwnexusedge.nexus.edge.agentscope.adapter;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.context.Scope;

/**
 * AgentScope Adapter 的 OpenTelemetry Trace 支持（P1-4）。
 *
 * <p>在真实执行链路中创建 span 并采集 Trace ID（32 位 hex）。AgentScope core 通过
 * {@code GlobalOpenTelemetry} 与 {@code OtelTracingMiddleware} 传播 span 上下文；
 * 本类在启动执行前创建 span（makeCurrent），使执行期间 AgentScope 的 span 成为子 span。
 *
 * <p>边界：未配置 OTel SDK 时（no-op provider）traceId 为全零（32 个 '0'），
 * 由测试与生产门禁拒绝；真实环境必须配置 OTel SDK 使 GlobalOpenTelemetry 产生真实 span。
 */
public final class TraceSupport {

    private TraceSupport() {
        // 工具类，禁止实例化
    }

    /**
     * 创建真实 span 并返回其 Trace ID（32 位 hex）。
     *
     * <p>span 在返回后保持 current 直至调用者 {@link #endCurrentSpan(Scope, Span)} 结束；
     * 期间执行的 AgentScope 调用应被纳入同一 trace。
     *
     * @return 采集结果（span 与 scope 需配对关闭）
     */
    public static TraceHandle startAndCaptureTraceId() {
        Span span = GlobalOpenTelemetry.getTracer("com.gwnexusedge.nexus.edge.agentscope")
                .spanBuilder("nexus-edge.agent.execution")
                .startSpan();
        Scope scope = span.makeCurrent();
        SpanContext sc = span.getSpanContext();
        return new TraceHandle(span, scope, sc.getTraceId());
    }

    /** 结束 span 与 scope（配对关闭）。 */
    public static void end(TraceHandle handle) {
        if (handle != null) {
            handle.scope.close();
            handle.span.end();
        }
    }

    /** 一个正在进行的 trace 采集句柄。 */
    public record TraceHandle(Span span, Scope scope, String traceId) {}
}
