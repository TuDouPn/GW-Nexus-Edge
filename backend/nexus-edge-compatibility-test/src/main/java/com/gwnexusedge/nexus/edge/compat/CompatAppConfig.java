package com.gwnexusedge.nexus.edge.compat;

import cn.dev33.satoken.interceptor.SaInterceptor;
import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 兼容性 Harness 应用装配配置（DEV-0002）。
 *
 * <p>只包含验证所需的最小装配，无业务功能：
 * <ul>
 *   <li><b>MyBatis-Plus 分页插件</b>（MP-5）：注册 {@link MybatisPlusInterceptor} +
 *       {@link PaginationInnerInterceptor}，分页查询 total/records 由真实数据库统计；</li>
 *   <li><b>Sa-Token 官方拦截器</b>（ST-8）：注册 {@link SaInterceptor}，使控制器上的
 *       {@code @SaCheckLogin/@SaCheckRole/@SaCheckPermission} 注解经真实
 *       Servlet/Filter/Interceptor 链路生效（不以 StpUtil 静态调用代替链路验证）。</li>
 * </ul>
 */
@Configuration
public class CompatAppConfig implements WebMvcConfigurer {

    /**
     * MyBatis-Plus 拦截器：分页插件（目标数据库 MySQL；DM8 环境可用时同配置验证）。
     *
     * @return MyBatis-Plus 拦截器（含分页）
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);
        interceptor.addInnerInterceptor(pagination);
        return interceptor;
    }

    /**
     * 注册 Sa-Token 官方拦截器：处理 {@code @SaCheck*} 注解。
     *
     * <p>拦截范围 {@code /compat/auth/**}：登录接口不需要鉴权，其余接口由注解控制。
     * 未登录抛 {@link cn.dev33.satoken.exception.NotLoginException}（映射 401），
     * 角色/权限不足抛 {@code NotRoleException/NotPermissionException}（映射 403），
     * 见 {@link CompatAuthExceptionHandler}。
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaInterceptor())
                .addPathPatterns("/compat/auth/**")
                .excludePathPatterns("/compat/auth/login");
    }
}
