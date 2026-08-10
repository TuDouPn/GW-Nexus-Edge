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
    private final AtomicBoolean failWith500 = new AtomicBoolean(false);

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

    public String lastRequestBody() {
        return lastRequestBody.get();
    }

    private void handleHealth(HttpExchange exchange) throws IOException {
        respond(exchange, 200, "{\"status\":\"ok\"}");
    }

    private void handleChatCompletions(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        lastRequestBody.set(body);

        if (failWith500.get()) {
            respond(exchange, 500, "{\"error\":{\"message\":\"injected server error\",\"type\":\"server_error\"}}");
            return;
        }

        boolean stream = body.contains("\"stream\":true");
        if (stream) {
            streamResponse(exchange);
        } else if (body.contains("\"tools\"")) {
            // 请求携带工具定义时，返回一次工具调用，验证官方 Tool Calling 链路。
            respond(exchange, 200, toolCallResponseBody());
        } else {
            respond(exchange, 200, nonStreamResponseBody());
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
