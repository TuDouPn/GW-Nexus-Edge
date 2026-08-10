package com.gwnexusedge.nexus.edge.agentscope.adapter;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;

/**
 * DEV-0001 测试用工具：验证 AgentScope 官方 Tool Calling 链路。
 *
 * <p>该工具通过官方 {@code @Tool}/{@code @ToolParam} 注解注册到官方 Toolkit，
 * 由 AgentScope 官方 Runtime 调用；工具本身是只读确定性实现，不产生任何副作用。
 */
public class EchoTextTool {

    /**
     * 回显输入文本（只读确定性工具）。
     *
     * @param text 待回显文本
     * @return 回显结果
     */
    @Tool(name = "echo_text", description = "回显输入的文本，用于验证 Tool Calling 链路")
    public String echoText(
            @ToolParam(name = "text", description = "需要回显的文本", required = true) String text) {
        return "回显: " + text;
    }
}
