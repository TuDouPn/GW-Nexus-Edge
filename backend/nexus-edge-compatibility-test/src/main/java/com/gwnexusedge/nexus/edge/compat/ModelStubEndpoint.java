package com.gwnexusedge.nexus.edge.compat;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * AgentScope 下游模型 Test Double（组合 Smoke Test 专用，@Profile("agentscope")）。
 *
 * <p>与 DEV-0001 的 {@code CompatEndpoint} 同属「下游模型 Test Double」先例：
 * AgentScope 官方 Harness/Core/模型扩展均为真实实现，仅模型下游端点为本地受控端点。
 * 该端点固定返回文本，用于验证 AgentScope 与外围依赖在<b>同一 Spring 上下文</b>中
 * 真实共存（SM-1）并完成一次真实调用。
 *
 * <p>仅存在于 compatibility-test 模块，不进入生产镜像（DEV-0002 §11）。
 */
@Component
@Profile("agentscope")
public class ModelStubEndpoint {

    /** OpenAI-compatible 聊天补全路径（与官方 OpenAIChatModel 默认一致）。 */
    private static final String CHAT_COMPLETIONS_PATH = "/v1/chat/completions";

    /** 固定返回文本（兼容性冒烟验证断言目标）。 */
    private static final String FIXED_TEXT = "兼容性冒烟验证回复";

    private HttpServer server;

    /**
     * 启动本地 HttpServer（随机端口）。
     *
     * @throws IOException 端口绑定失败
     */
    @PostConstruct
    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(CHAT_COMPLETIONS_PATH, this::handleChatCompletions);
        server.start();
    }

    /**
     * 停止 HttpServer（幂等）。
     */
    @PreDestroy
    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    /**
     * 返回端点 Base URL（供模型注册使用）。
     *
     * @return http://127.0.0.1:port
     */
    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    /**
     * 处理 /v1/chat/completions：流式请求返回 SSE chunk，非流式返回 JSON。
     *
     * @param exchange HTTP 交换对象
     * @throws IOException 写出失败
     */
    private void handleChatCompletions(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        boolean stream = body.contains("\"stream\":true");
        if (stream) {
            streamResponse(exchange);
        } else {
            jsonResponse(exchange);
        }
    }

    /** 非流式 JSON 响应（固定文本）。 */
    private void jsonResponse(HttpExchange exchange) throws IOException {
        String json = """
                {
                  "id": "chatcmpl-compat",
                  "object": "chat.completion",
                  "created": 1700000000,
                  "model": "compat-model",
                  "choices": [{"index": 0, "message": {"role": "assistant", "content": "%s"}, "finish_reason": "stop"}],
                  "usage": {"prompt_tokens": 5, "completion_tokens": 5, "total_tokens": 10}
                }
                """.formatted(FIXED_TEXT);
        respond(exchange, 200, json);
    }

    /** 流式 SSE 响应（固定文本，分两个 chunk + [DONE]）。 */
    private void streamResponse(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
        exchange.sendResponseHeaders(200, 0);
        try (OutputStream out = exchange.getResponseBody()) {
            String[] chunks = {
                "data: {\"id\":\"chatcmpl-compat\",\"object\":\"chat.completion.chunk\","
                    + "\"choices\":[{\"index\":0,\"delta\":{\"role\":\"assistant\",\"content\":\""
                    + FIXED_TEXT + "\"},\"finish_reason\":null}]}\n\n",
                "data: {\"id\":\"chatcmpl-compat\",\"object\":\"chat.completion.chunk\","
                    + "\"choices\":[{\"index\":0,\"delta\":{},\"finish_reason\":\"stop\"}]}\n\n",
                "data: [DONE]\n\n"
            };
            for (String chunk : chunks) {
                out.write(chunk.getBytes(StandardCharsets.UTF_8));
                out.flush();
            }
        }
    }

    /** 写出 JSON 响应。 */
    private static void respond(HttpExchange exchange, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
