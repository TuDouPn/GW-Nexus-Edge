package com.gwnexusedge.nexus.edge.agentscope.adapter;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 长期 Runtime Identity 映射（第七轮 P0-1/P0-4）。
 *
 * <p>把 Tenant/Workspace/User/Session 业务标识映射为 AgentScope 的
 * {@code scopedUserId}/{@code scopedSessionId}，满足：
 * <ul>
 *   <li><b>稳定</b>：确定性映射，同一输入恒等输出——跨实例/跨重启/多副本可重建；</li>
 *   <li><b>无碰撞</b>：分量独立 Base64URL 编码并以 {@code '.'} 连接。编码字母表
 *       （A-Za-z0-9-_）不含分隔符 {@code '.'}，Base64URL 为单射编码，
 *       任意不同输入组合必得不同 scoped 标识；</li>
 *   <li><b>路径安全</b>：Base64URL 字母表不含 {@code '/'}、{@code '\'}、{@code '..'}、
 *       冒号与空白，可直接作为 State Store / Memory 目录段，避免依赖官方
 *       {@code JsonFileAgentStateStore} 的有损文件名净化（{@code safeSegment}）带来的碰撞。</li>
 * </ul>
 *
 * <p>原始业务标识不进入 scoped 标识，而保留在 RuntimeContext extras
 * （workspaceId/tenantId/sessionId），供 Agent 与持久化层按需读取（P0-1）。
 *
 * <p>fail-closed（P0-4）：workspaceId/tenantId 为 null/空白时拒绝映射，
 * 禁止以 null→空串或冒号拼接等临时方案掩盖缺失。
 */
public final class AgentRuntimeIdentityMapper {

    /** 编码分量间的分隔符（不在 Base64URL 字母表内，保证无碰撞拼接）。 */
    private static final char SEPARATOR = '.';

    private AgentRuntimeIdentityMapper() {
        // 工具类，禁止实例化
    }

    /**
     * 映射结果：AgentScope 视角的稳定身份对。
     *
     * @param scopedUserId    Tenant/Workspace/User 复合的 scoped 用户标识（路径安全）
     * @param scopedSessionId Session 的 scoped 会话标识（路径安全）
     */
    public record ScopedIdentity(String scopedUserId, String scopedSessionId) {
        public ScopedIdentity {
            if (scopedUserId == null || scopedUserId.isBlank()) {
                throw new IllegalArgumentException("scopedUserId 不允许为空");
            }
            if (scopedSessionId == null || scopedSessionId.isBlank()) {
                throw new IllegalArgumentException("scopedSessionId 不允许为空");
            }
        }
    }

    /**
     * 映射完整业务身份为 AgentScope scoped 身份（fail-closed）。
     *
     * @param tenantId    租户标识（不允许 null/空白）
     * @param workspaceId 工作空间标识（不允许 null/空白）
     * @param userId      业务用户标识
     * @param sessionId   业务会话标识
     * @return scopedUserId/scopedSessionId
     */
    public static ScopedIdentity map(String tenantId, String workspaceId,
                                     String userId, String sessionId) {
        requireNonBlank(tenantId, "tenantId");
        requireNonBlank(workspaceId, "workspaceId");
        requireNonBlank(userId, "userId");
        requireNonBlank(sessionId, "sessionId");
        return new ScopedIdentity(
                scopedUserId(tenantId, workspaceId, userId),
                scopedSessionId(sessionId));
    }

    /**
     * scopedUserId = {@code b64(tenant).b64(workspace).b64(user)}。
     *
     * <p>Tenant/Workspace/User 全部参与编码：不同租户或不同工作空间下相同 user 的
     * AgentScope 会话（AgentState/Memory 键控）互不共享（P0-1）。
     */
    public static String scopedUserId(String tenantId, String workspaceId, String userId) {
        requireNonBlank(tenantId, "tenantId");
        requireNonBlank(workspaceId, "workspaceId");
        requireNonBlank(userId, "userId");
        return encodeComponent(tenantId) + SEPARATOR
                + encodeComponent(workspaceId) + SEPARATOR
                + encodeComponent(userId);
    }

    /**
     * scopedSessionId = {@code b64(session)}。
     *
     * <p>会话标识已随 scopedUserId 的租户/工作空间维度隔离；此处仅做路径安全编码，
     * 保证原始业务 session 中可能出现的分隔符/路径段不进入目录结构。
     */
    public static String scopedSessionId(String sessionId) {
        requireNonBlank(sessionId, "sessionId");
        return encodeComponent(sessionId);
    }

    /**
     * 分量编码：Base64 URL 安全、无填充（字母表 A-Za-z0-9-_，路径安全且单射）。
     */
    private static String encodeComponent(String value) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static void requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " 不允许为空（Runtime Identity fail-closed）");
        }
    }
}
