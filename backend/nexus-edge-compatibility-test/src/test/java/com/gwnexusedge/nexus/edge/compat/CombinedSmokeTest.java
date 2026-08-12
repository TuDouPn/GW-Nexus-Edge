package com.gwnexusedge.nexus.edge.compat;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.UserMessage;
import io.agentscope.harness.agent.HarnessAgent;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DEV-0002 完整组合 Smoke Test（§9：SM-1/SM-3）。
 *
 * <p>在<b>同一个 Spring Boot 应用上下文</b>中同时装配并真实运行：
 * Java 21 + Spring Boot 4.1.0 + AgentScope 2.0.1 + MyBatis-Plus + Sa-Token +
 * Flyway + Redis Client + JDBC Driver（MySQL）：
 * <ul>
 *   <li>SM-1：单一应用上下文启动，全部依赖装配成功（含 AgentScope HarnessAgent Bean）；</li>
 *   <li>真实操作：MyBatis-Plus 真实 MySQL 查询（经 Flyway 迁移的表）、Redis 真实读写、
 *       AgentScope 真实调用（本地 stub 下游 Test Double，DEV-0001 先例）；</li>
 *   <li>SM-3：传递依赖冲突检查见 COMPATIBILITY_REPORT.md（dependency:tree 证据）。</li>
 * </ul>
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"mysql", "agentscope"})
class CombinedSmokeTest {

    /** 固定版本 MySQL 镜像（MyBatis-Plus + Flyway + JDBC）。 */
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.5");

    /** 固定版本 Redis 镜像（Spring Data Redis + Lettuce）。 */
    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.4.2")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void combinedProperties(DynamicPropertyRegistry registry) {
        // MySQL（MyBatis-Plus/Flyway/JDBC）。
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        // Redis（Spring Data Redis + Lettuce）。
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired
    CompatUserMapper mapper;

    @Autowired
    StringRedisTemplate redisTemplate;

    @Autowired
    HarnessAgent harnessAgent;

    @Test
    @DisplayName("SM-1：单一上下文组合启动 + 真实 MyBatis/Redis/AgentScope 操作")
    void combinedContextStartsWithAllDependenciesAndExecutes() {
        // SM-1：AgentScope HarnessAgent Bean 真实存在（官方实例标识）。
        assertNotNull(harnessAgent, "AgentScope HarnessAgent 应装配为 Bean");
        assertNotNull(harnessAgent.getAgentId(), "AgentScope 应返回真实 Agent 标识");

        // 真实 MyBatis-Plus 查询（表 compat_user 由 Flyway MySQL 迁移在真实 MySQL 创建）。
        CompatUser user = new CompatUser();
        user.setName("smoke-" + UUID.randomUUID());
        user.setScore(55);
        mapper.insert(user);
        assertNotNull(user.getId(), "MyBatis-Plus 插入应回填真实主键");
        CompatUser loaded = mapper.selectById(user.getId());
        assertEquals(user.getName(), loaded.getName(), "MyBatis-Plus 查询应命中真实 MySQL");

        // 真实 Redis 读写（Spring Data Redis + Lettuce）。
        String redisKey = "compat:smoke:" + UUID.randomUUID();
        String redisValue = "smoke-value-" + System.nanoTime();
        redisTemplate.opsForValue().set(redisKey, redisValue);
        assertEquals(redisValue, redisTemplate.opsForValue().get(redisKey), "Redis 读写应往返一致");
        redisTemplate.delete(redisKey);

        // 真实 AgentScope 调用（组合上下文内执行；下游为本地 stub Test Double）。
        RuntimeContext ctx = RuntimeContext.builder()
                .userId("smoke-user")
                .sessionId("smoke-session")
                .build();
        Msg result = harnessAgent.call(List.of(new UserMessage("请回复冒烟验证")), ctx)
                .block(Duration.ofMinutes(2));
        assertNotNull(result, "AgentScope 调用应返回结果");
        String text = result.getTextContent();
        assertNotNull(text, "AgentScope 结果应包含文本");
        assertTrue(text.contains("兼容性冒烟验证回复"),
                "AgentScope 结果应包含 stub 固定文本，实际: " + text);
    }
}
