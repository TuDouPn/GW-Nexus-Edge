package com.gwnexusedge.nexus.edge.agentscope.adapter;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;

/**
 * DEV-0001 测试用 OpenTelemetry SDK 工具。
 *
 * <p>AgentScope core 通过 {@code GlobalOpenTelemetry} 与 {@code OtelTracingMiddleware}
 * 产生真实 span。测试注入 SDK，使执行期间可采集真实 Trace ID，
 * 用于验证 Task → Execution → Trace 关联（12 §6）并拒绝 "unassigned" 等伪造值。
 *
 * <p>实现说明：只注册 {@link SdkTracerProvider} 即可创建真实 span 并读取其 Trace ID。
 * {@link #init()} 使用 {@code buildAndRegisterGlobal()} 并在已初始化时跳过（幂等），
 * 避免 {@code GlobalOpenTelemetry.set} 重复调用崩溃。
 */
public final class TestOtel {

    private static volatile boolean initialized = false;

    private TestOtel() {
        // 工具类，禁止实例化
    }

    /**
     * 幂等初始化全局 OTel SDK（重复调用安全）。
     */
    public static synchronized OpenTelemetry init() {
        if (initialized) {
            return GlobalOpenTelemetry.get();
        }
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder().build();
        OpenTelemetrySdk sdk = OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .buildAndRegisterGlobal();
        initialized = true;
        return sdk;
    }

    /** 创建一个真实 span，并返回其 Trace ID（32 位 hex）。 */
    public static String newTraceId() {
        init();
        Span span = GlobalOpenTelemetry.getTracer("nexus-edge-test")
                .spanBuilder("dev-0001-trace")
                .startSpan();
        try (Scope ignored = span.makeCurrent()) {
            SpanContext sc = span.getSpanContext();
            return sc.getTraceId();
        } finally {
            span.end();
        }
    }

    /** 断言一个 traceId 是真实的 32 位十六进制 OTel trace id（拒绝空值与全零）。 */
    public static boolean isRealTraceId(String traceId) {
        return traceId != null
                && traceId.matches("[0-9a-f]{32}")
                && !traceId.matches("0{32}")
                && !"unassigned".equals(traceId);
    }
}
