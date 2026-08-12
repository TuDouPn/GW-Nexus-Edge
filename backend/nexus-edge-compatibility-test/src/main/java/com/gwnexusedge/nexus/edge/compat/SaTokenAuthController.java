package com.gwnexusedge.nexus.edge.compat;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.stp.StpUtil;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sa-Token 真实 HTTP 认证链路测试接口（ST-8）。
 *
 * <p>仅存在于 {@code nexus-edge-compatibility-test} 模块（DEV-0002 §11），
 * 不进入生产模块或生产镜像。全部行为经真实 Servlet/Filter/Interceptor 链路：
 * <ul>
 *   <li>{@code POST /compat/auth/login}：登录（放行，不鉴权）；</li>
 *   <li>{@code GET /compat/auth/me}：需登录（{@code @SaCheckLogin}）；</li>
 *   <li>{@code GET /compat/auth/role-check}：需 admin 角色（{@code @SaCheckRole}）；</li>
 *   <li>{@code GET /compat/auth/permission-check}：需 user:delete 权限（{@code @SaCheckPermission}）；</li>
 *   <li>{@code POST /compat/auth/logout}：注销当前会话。</li>
 * </ul>
 * 未登录/角色或权限不足时由 {@link CompatAuthExceptionHandler} 映射为 401/403。
 */
@RestController
@RequestMapping("/compat/auth")
public class SaTokenAuthController {

    /**
     * 登录：为指定用户建立会话并返回 Token。
     *
     * @param name 测试用户名（sa-admin / sa-normal）
     * @return 登录 Token
     */
    @PostMapping("/login")
    public Map<String, String> login(@RequestParam String name) {
        StpUtil.login(name);
        return Map.of("token", StpUtil.getTokenValue());
    }

    /**
     * 需登录接口：返回当前登录用户。
     *
     * @return 当前登录用户标识
     */
    @SaCheckLogin
    @GetMapping("/me")
    public Map<String, String> me() {
        return Map.of("loginId", StpUtil.getLoginIdAsString());
    }

    /**
     * 需 admin 角色接口。
     *
     * @return 角色校验通过标记
     */
    @SaCheckRole("admin")
    @GetMapping("/role-check")
    public Map<String, String> roleCheck() {
        return Map.of("result", "role-ok");
    }

    /**
     * 需 user:delete 权限接口。
     *
     * @return 权限校验通过标记
     */
    @SaCheckPermission("user:delete")
    @GetMapping("/permission-check")
    public Map<String, String> permissionCheck() {
        return Map.of("result", "perm-ok");
    }

    /**
     * 注销当前会话（Token 失效）。
     *
     * @return 注销结果
     */
    @PostMapping("/logout")
    public Map<String, String> logout() {
        StpUtil.logout();
        return Map.of("result", "logged-out");
    }
}
