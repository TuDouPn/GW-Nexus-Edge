package com.gwnexusedge.nexus.edge.compat;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 兼容性 Harness 的 Sa-Token 异常映射（ST-8）。
 *
 * <p>把真实拦截器链抛出的 Sa-Token 异常映射为确定的 HTTP 状态：
 * <ul>
 *   <li>{@link NotLoginException} → <b>401 Unauthorized</b>（无 Token/错误 Token/已注销）；</li>
 *   <li>{@link NotRoleException} / {@link NotPermissionException} → <b>403 Forbidden</b>。</li>
 * </ul>
 * 仅存在于 compatibility-test 模块，不属于业务错误处理。
 */
@RestControllerAdvice
public class CompatAuthExceptionHandler {

    /**
     * 未登录/Token 无效：映射 401。
     *
     * @return 脱敏错误响应
     */
    @ExceptionHandler(NotLoginException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Map<String, String> handleNotLogin(NotLoginException e) {
        return Map.of("error", "unauthorized", "type", e.getType());
    }

    /**
     * 角色不足：映射 403。
     *
     * @return 脱敏错误响应
     */
    @ExceptionHandler(NotRoleException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Map<String, String> handleNotRole(NotRoleException e) {
        return Map.of("error", "forbidden-role");
    }

    /**
     * 权限不足：映射 403。
     *
     * @return 脱敏错误响应
     */
    @ExceptionHandler(NotPermissionException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Map<String, String> handleNotPermission(NotPermissionException e) {
        return Map.of("error", "forbidden-permission");
    }
}
