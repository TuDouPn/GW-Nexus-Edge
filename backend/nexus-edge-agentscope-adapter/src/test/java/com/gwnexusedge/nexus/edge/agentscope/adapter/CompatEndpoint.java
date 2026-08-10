package com.gwnexusedge.nexus.edge.agentscope.adapter;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 受控 OpenAI-compatible 测试端点实现（JDK 内置 HttpServer，零额外依赖）。
 *
 * <p>行为：
 * <ul>
 *   <li>{@code /v1/chat/completions}：非流式返回固定文本；流式返回 SSE chunk；</li>
 *   <li>可注入 500 错误以验证官方 Provider 的错误传播；</li>
 *   <li>可记录最近一次请求体，供断言官方 Provider 的真实 HTTP 请求内容。</li>
 * </ul>
 *
 * <p>这是下游模型 Test Double；所有 AgentScope 代码均为官方真实实现。
 */
public final class CompatEndpoint implements AutoCloseable {

    private final HttpServer server;
    private final AtomicReference<String> lastRequestBody = new AtomicReference<>();
    private final java.util.List<String> allRequestBodies = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final AtomicReference<String> lastAuthorization = new AtomicReference<>();
    private final java.util.List<String> allAuthorizations = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final AtomicBoolean failWith500 = new AtomicBoolean(false);
    private final AtomicBoolean structuredReply = new AtomicBoolean(false);
    private final java.util.concurrent.atomic.AtomicLong artificialDelayMillis = new java.util.concurrent.atomic.AtomicLong(0);
    private final java.util.concurrent.atomic.AtomicInteger toolCallRounds = new java.util.concurrent.atomic.AtomicInteger(0);

    /**
     * 启动测试端点。
     *
     * @param port 监听端口（0 表示自动分配）
     */
    public CompatEndpoint(int port) throws IOException {
        this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext(CompatEndpointSpec.CHAT_COMPLETIONS_PATH, this::handleChatCompletions);
        server.createContext(CompatEndpointSpec.HEALTH_PATH, this::handleHealth);
        server.start();
    }

    public int getPort() {
        return server.getAddress().getPort();
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + getPort();
    }

    /** 注入 500 错误，用于验证官方 Provider 的错误传播。 */
    public void setFailWith500(boolean fail) {
        failWith500.set(fail);
    }

    /** 切换为结构化输出响应（返回合法 JSON 结构化结果）。 */
    public void setStructuredReply(boolean structured) {
        structuredReply.set(structured);
    }

    /** 设置每个请求的人工延迟（毫秒），用于验证取消运行中任务。 */
    public void setArtificialDelayMillis(long millis) {
        artificialDelayMillis.set(millis);
    }

    public String lastRequestBody() {
        return lastRequestBody.get();
    }

    /** 全部请求体列表（用于观察完整调用序列，例如 Memory 中间件与主调用的关系）。 */
    public java.util.List<String> allRequestBodies() {
        return java.util.List.copyOf(allRequestBodies);
    }

    /** 最近一次请求的 Authorization 头（P0-3 Secret 解析验证）。 */
    public String lastAuthorization() {
        return lastAuthorization.get();
    }

    /** 全部请求的 Authorization 头。 */
    public java.util.List<String> allAuthorizations() {
        return java.util.List.copyOf(allAuthorizations);
    }

    /** 重置记录的请求体与 Authorization，便于用例间隔离。 */
    public void resetRequests() {
        allRequestBodies.clear();
        lastRequestBody.set(null);
        allAuthorizations.clear();
        lastAuthorization.set(null);
    }

    /** 重置工具调用轮次计数（每个用例独立验证首轮 tool_call）。 */
    public void resetToolCallRounds() {
        toolCallRounds.set(0);
    }

    private void handleHealth(HttpExchange exchange) throws IOException {
        respond(exchange, 200, "{\"status\":\"ok\"}");
    }

    private void handleChatCompletions(HttpExchange exchange) throws IOException {
        long delay = artificialDelayMillis.get();
        if (delay > 0) {
            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        lastRequestBody.set(body);
        allRequestBodies.add(body);
        // 记录 Authorization 头（P0-3：验证 Secret 解析值真实进入请求）。
        String auth = exchange.getRequestHeaders().getFirst("Authorization");
        lastAuthorization.set(auth);
        allAuthorizations.add(auth);

        if (failWith500.get()) {
            respond(exchange, 500, "{\"error\":{\"message\":\"injected server error\",\"type\":\"server_error\"}}");
            return;
        }

        boolean stream = body.contains("\"stream\":true");
        boolean hasTools = body.contains("\"tools\"");
        if (structuredReply.get()) {
            // 结构化输出：返回合法 JSON（native response_format 或 generate_response 工具结果）。
            if (stream) {
                streamStructuredResponse(exchange);
            } else {
                respond(exchange, 200, structuredResponseBody());
            }
        } else if (stream && hasTools && toolCallRounds.incrementAndGet() == 1) {
            // 主调用为流式且携带工具定义：首轮以 SSE 返回一次 tool_call 验证 Tool Calling 链路。
            // 后续轮次（会话已含 tool result）返回普通文本，避免无限 Tool 循环。
            streamToolCallResponse(exchange);
        } else if (stream) {
            streamResponse(exchange);
        } else if (hasTools && toolCallRounds.incrementAndGet() == 1) {
            // 非流式且携带工具定义：首轮返回一次工具调用。
            respond(exchange, 200, toolCallResponseBody());
        } else {
            respond(exchange, 200, nonStreamResponseBody());
        }
    }

    private String structuredResponseBody() {
        // 与 AnalysisResult(conclusion, score, recommendations) 匹配的合法 JSON。
        return """
                {
                  "id": "chatcmpl-test-so",
                  "object": "chat.completion",
                  "created": 1700000000,
                  "model": "test-model",
                  "choices": [{
                    "index": 0,
                    "message": {
                      "role": "assistant",
                      "content": "{"conclusion":"经营状况总体稳健","score":0.85,"recommendations":["优化现金流","控制成本"]}"
                    },
                    "finish_reason": "stop"
                  }],
                  "usage": {"prompt_tokens": 10, "completion_tokens": 20, "total_tokens": 30}
                }
                """;
    }

    private void streamStructuredResponse(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
        exchange.sendResponseHeaders(200, 0);
        try (OutputStream out = exchange.getResponseBody()) {
            String[] chunks = {
                "data: {\"id\":\"chatcmpl-test-so\",\"object\":\"chat.completion.chunk\","
                    + "\"choices\":[{\"index\":0,\"delta\":{\"role\":\"assistant\",\"content\":"
                    + "\"{\\\"conclusion\\\":\\\"经营状况总体稳健\\\",\\\"score\\\":0.85,"
                    + "\\\"recommendations\\\":[\\\"优化现金流\\\",\\\"控制成本\\\"]}\"},\"finish_reason\":null}]}\n\n",
                "data: {\"id\":\"chatcmpl-test-so\",\"object\":\"chat.completion.chunk\","
                    + "\"choices\":[{\"index\":0,\"delta\":{},\"finish_reason\":\"stop\"}]}\n\n",
                "data: [DONE]\n\n"
            };
            for (String chunk : chunks) {
                out.write(chunk.getBytes(StandardCharsets.UTF_8));
                out.flush();
            }
        }
    }

    private void streamToolCallResponse(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
        exchange.sendResponseHeaders(200, 0);
        try (OutputStream out = exchange.getResponseBody()) {
            String[] chunks = {
                "data: {\"id\":\"chatcmpl-test-tool\",\"object\":\"chat.completion.chunk\","
                    + "\"choices\":[{\"index\":0,\"delta\":{\"role\":\"assistant\",\"content\":null},"
                    + "\"finish_reason\":null}]}\n\n",
                "data: {\"id\":\"chatcmpl-test-tool\",\"object\":\"chat.completion.chunk\","
                    + "\"choices\":[{\"index\":0,\"delta\":{\"tool_calls\":[{\"index\":0,\"id\":\"call_test_0001\","
                    + "\"type\":\"function\",\"function\":{\"name\":\"echo_text\",\"arguments\":\"{\\\"text\\\":\\\"来自工具的测试参数\\\"}\"}}]},"
                    + "\"finish_reason\":null}]}\n\n",
                "data: {\"id\":\"chatcmpl-test-tool\",\"object\":\"chat.completion.chunk\","
                    + "\"choices\":[{\"index\":0,\"delta\":{},\"finish_reason\":\"tool_calls\"}]}\n\n",
                "data: [DONE]\n\n"
            };
            for (String chunk : chunks) {
                out.write(chunk.getBytes(StandardCharsets.UTF_8));
                out.flush();
            }
        }
    }

    private String toolCallResponseBody() {
        return """
                {
                  "id": "chatcmpl-test-tool",
                  "object": "chat.completion",
                  "created": 1700000000,
                  "model": "test-model",
                  "choices": [{
                    "index": 0,
                    "message": {
                      "role": "assistant",
                      "content": null,
                      "tool_calls": [{
                        "id": "call_test_0001",
                        "type": "function",
                        "function": {
                          "name": "echo_text",
                          "arguments": "{\\"text\\":\\"来自工具的测试参数\\"}"
                        }
                      }]
                    },
                    "finish_reason": "tool_calls"
                  }],
                  "usage": {"prompt_tokens": 12, "completion_tokens": 10, "total_tokens": 22}
                }
                """;
    }

    private String nonStreamResponseBody() {
        return """
                {
                  "id": "chatcmpl-test-0001",
                  "object": "chat.completion",
                  "created": 1700000000,
                  "model": "test-model",
                  "choices": [{
                    "index": 0,
                    "message": {
                      "role": "assistant",
                      "content": "测试端点的固定回复。"
                    },
                    "finish_reason": "stop"
                  }],
                  "usage": {"prompt_tokens": 10, "completion_tokens": 8, "total_tokens": 18}
                }
                """;
    }

    private void streamResponse(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
        exchange.sendResponseHeaders(200, 0);
        try (OutputStream out = exchange.getResponseBody()) {
            String[] chunks = {
                "data: {\"id\":\"chatcmpl-test-0001\",\"object\":\"chat.completion.chunk\","
                    + "\"choices\":[{\"index\":0,\"delta\":{\"role\":\"assistant\",\"content\":\"测试\"},\"finish_reason\":null}]}\n\n",
                "data: {\"id\":\"chatcmpl-test-0001\",\"object\":\"chat.completion.chunk\","
                    + "\"choices\":[{\"index\":0,\"delta\":{\"content\":\"流式\"},\"finish_reason\":null}]}\n\n",
                "data: {\"id\":\"chatcmpl-test-0001\",\"object\":\"chat.completion.chunk\","
                    + "\"choices\":[{\"index\":0,\"delta\":{},\"finish_reason\":\"stop\"}]}\n\n",
                "data: [DONE]\n\n"
            };
            for (String chunk : chunks) {
                out.write(chunk.getBytes(StandardCharsets.UTF_8));
                out.flush();
            }
        }
    }

    private static void respond(HttpExchange exchange, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
