package com.gwnexusedge.nexus.edge.compat;

import cn.dev33.satoken.stp.StpInterface;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Sa-Token 角色与权限数据源实现（ST-3：角色与权限校验）。
 *
 * <p>兼容性 Harness 专用：为测试用户提供确定性角色/权限映射（仅测试数据，非业务）。
 * <ul>
 *   <li>{@code sa-admin}：角色 {@code admin}，权限 {@code user:delete}（登录后可访问受保护接口）；</li>
 *   <li>{@code sa-normal}：无角色/权限（登录后访问受保护接口应被 403 拒绝）。</li>
 * </ul>
 */
@Component
public class CompatStpInterface implements StpInterface {

    /** 管理员用户：拥有 admin 角色与 user:delete 权限。 */
    private static final String ADMIN_USER = "sa-admin";

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        if (ADMIN_USER.equals(loginId)) {
            return List.of("user:delete");
        }
        return List.of();
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        if (ADMIN_USER.equals(loginId)) {
            return List.of("admin");
        }
        return List.of();
    }
}
