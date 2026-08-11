package com.gwnexusedge.nexus.edge.agentscope.adapter;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import java.util.List;

/**
 * DEV-0001 测试用 OpenTelemetry SDK 工具（第四轮：InMemorySpanExporter）。
 *
 * <p>AgentScope core 通过 {@code GlobalOpenTelemetry} 与 {@code OtelTracingMiddleware}
 * 产生真实 span。测试注入 SDK + InMemorySpanExporter，可：
 * <ul>
 *   <li>产生真实 Trace ID（拒绝 "unassigned" 等伪造值）；</li>
 *   <li>验证父子 Span 关联（Adapter 的 {@code nexus-edge.agent.execution} 为父，
 *       AgentScope 的 {@code invoke_agent <name>} 为子）；</li>
 *   <li>验证所有 span 已结束（doFinally 幂等）。</li>
 * </ul>
 */
public final class TestOtel {

    private static volatile boolean initialized = false;
    private static volatile InMemorySpanExporter exporter;

    private TestOtel() {
        // 工具类，禁止实例化
    }

    /**
     * 幂等初始化全局 OTel SDK + InMemorySpanExporter。
     */
    public static synchronized OpenTelemetry init() {
        if (initialized) {
            return GlobalOpenTelemetry.get();
        }
        exporter = InMemorySpanExporter.create();
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                .build();
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

    /** 清除已导出的 span（用例间隔离）。 */
    public static synchronized void resetExporter() {
        if (exporter != null) {
            exporter.reset();
        }
    }

    /** 当前已导出的全部 span（只读副本）。 */
    public static synchronized List<SpanData> exportedSpans() {
        init();
        return List.copyOf(exporter.getFinishedSpanItems());
    }

    /**
     * 查找指定名称的已导出 span。
     */
    public static synchronized SpanData findSpan(String name) {
        return exportedSpans().stream()
                .filter(s -> s.getName().equals(name))
                .findFirst()
                .orElse(null);
    }

    /** 断言 traceId 下存在父子 span 关联：父 {@code nexus-edge.agent.execution}，子 {@code invoke_agent *}。 */
    public static boolean hasParentChildSpans(String traceId) {
        List<SpanData> spans = exportedSpans();
        SpanData parent = spans.stream()
                .filter(s -> s.getTraceId().equals(traceId)
                        && s.getName().equals("nexus-edge.agent.execution"))
                .findFirst()
                .orElse(null);
        if (parent == null) {
            return false;
        }
        // 子 span：AgentScope OtelTracingMiddleware 创建的 invoke_agent <name>，其 parentSpanId 指向父。
        return spans.stream().anyMatch(s -> s.getTraceId().equals(traceId)
                && s.getName().startsWith("invoke_agent ")
                && s.getParentSpanId().equals(parent.getSpanId()));
    }
}
