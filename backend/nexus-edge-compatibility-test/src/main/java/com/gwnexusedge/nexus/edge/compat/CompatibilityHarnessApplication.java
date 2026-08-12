package com.gwnexusedge.nexus.edge.compat;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * DEV-0002（G-02）外围依赖兼容性验证 Harness 应用入口。
 *
 * <p>这是<b>仅用于兼容性验证</b>的 Spring Boot 应用：
 * <ul>
 *   <li>验证 MyBatis-Plus / Sa-Token / JDBC / Redis / Flyway / AgentScope 在
 *       Java 21 + Spring Boot 4.1.0 + AgentScope 2.0.1 固定核心下的真实组合装配；</li>
 *   <li>属于 {@code nexus-edge-compatibility-test} 测试模块，<b>不进入生产镜像、
 *       不被生产模块依赖、不包含正式业务功能</b>（DEV-0002 §11）。</li>
 * </ul>
 *
 * <p>各测试类通过 {@code @SpringBootTest} + 独立 Profile/Test Slice 启动本应用，
 * 避免多数据源自动配置互相污染（DEV-0002 §11）。
 */
@SpringBootApplication
public class CompatibilityHarnessApplication {

    /**
     * Harness 入口（仅测试使用；生产不存在该入口）。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(CompatibilityHarnessApplication.class, args);
    }
}
