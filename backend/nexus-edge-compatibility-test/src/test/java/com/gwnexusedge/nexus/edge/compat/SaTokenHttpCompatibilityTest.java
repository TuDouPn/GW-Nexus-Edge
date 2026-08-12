package com.gwnexusedge.nexus.edge.compat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DEV-0002 Sa-Token 兼容性测试（§8.2：ST-2~ST-8）。
 *
 * <p>使用官方 Spring Boot 4 Starter（sa-token-spring-boot4-starter 1.45.0）+
 * Spring Boot 嵌入式 Web Server + 随机端口，<b>全部经真实 HTTP Servlet/Filter/Interceptor
 * 链路验证</b>（ST-8，不以 StpUtil 静态调用代替链路验证）：
 * <ul>
 *   <li>@Order(1)：HTTP 登录获得 Token；携带 Token 访问受保护接口（身份/角色/权限）；
 *       Redis 登录态持久化；无 Token/错误 Token 被拒绝（401）；</li>
 *   <li>@Order(2)：关闭并创建<b>第二个 Spring Context</b>后，原 Token 经 HTTP 验证会话恢复；
 *       注销后旧 Token 被拒绝（401）；</li>
 *   <li>@Order(3)：角色不足被拒绝（403）。</li>
 * </ul>
 * 测试接口只存在于 nexus-edge-compatibility-test 模块（DEV-0002 §11），不进生产模块/镜像。
 */
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        // 本测试不依赖数据库：排除 JDBC/MyBatis-Plus 自动配置，避免无数据源启动失败。
        // 注：Spring Boot 4 将 JDBC 自动配置模块化到 org.springframework.boot.jdbc.autoconfigure
        // 包（真实运行日志实证：旧包名排除无效导致 Hikari 尝试建数据源）。
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                + "org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration,"
                + "com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration"
})
@ActiveProfiles("redis")
class SaTokenHttpCompatibilityTest {

    /** 固定版本 Redis 镜像（Sa-Token Redis 持久化）。 */
    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.4.2")
            .withExposedPorts(6379);

    /** 跨 Context 共享的登录 Token（Session 恢复验证）。 */
    private static String sharedToken;

    @LocalServerPort
    private int port;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private final HttpClient http = HttpClient.newHttpClient();

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @AfterAll
    static void tearDown() {
        if (REDIS.isRunning()) {
            REDIS.stop();
        }
    }

    @Test
    @Order(1)
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    @DisplayName("ST-2/ST-3/ST-5/ST-8：HTTP 登录、身份/角色/权限、Redis 持久化、无/错 Token 拒绝")
    void loginAndVerifyAuthFlowViaHttp() throws Exception {
        // ST-2：HTTP 登录接口获得 Token。
        sharedToken = login("sa-admin");
        assertNotNull(sharedToken, "登录应返回 Token");
        assertFalse(sharedToken.isBlank(), "Token 不得为空");

        // ST-2/ST-8：携带 Token 访问受保护接口——身份读取。
        HttpResponse<String> me = get("/compat/auth/me", sharedToken);
        assertEquals(200, me.statusCode(), "携带 Token 应可访问受保护接口");
        assertTrue(me.body().contains("sa-admin"), "登录身份应正确");

        // ST-3/ST-8：角色与权限校验。
        HttpResponse<String> role = get("/compat/auth/role-check", sharedToken);
        assertEquals(200, role.statusCode(), "admin 角色应通过角色校验");
        assertTrue(role.body().contains("role-ok"));
        HttpResponse<String> perm = get("/compat/auth/permission-check", sharedToken);
        assertEquals(200, perm.statusCode(), "user:delete 权限应通过权限校验");
        assertTrue(perm.body().contains("perm-ok"));

        // ST-5/ST-8：Redis 登录态持久化——Sa-Token 写入 Redis（satoken: 前缀键存在）。
        assertNotNull(redisTemplate, "StringRedisTemplate 应注入");
        assertFalse(redisTemplate.keys("satoken:*").isEmpty(),
                "登录后 Sa-Token 应在 Redis 中存在 satoken:* 键（Redis 持久化）");

        // ST-8：无 Token 访问被拒绝（401）。
        HttpResponse<String> noToken = get("/compat/auth/me", null);
        assertEquals(401, noToken.statusCode(), "无 Token 应被 401 拒绝");

        // ST-8：错误 Token 访问被拒绝（401）。
        HttpResponse<String> wrongToken = get("/compat/auth/me", "invalid-token-" + System.nanoTime());
        assertEquals(401, wrongToken.statusCode(), "错误 Token 应被 401 拒绝");
    }

    @Test
    @Order(2)
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    @DisplayName("ST-6/ST-8：第二 Spring Context 会话恢复 + 注销后旧 Token 拒绝")
    void sessionRecoveredInSecondContextAndLogoutInvalidatesToken() throws Exception {
        // ST-6/ST-8：第一个 Context 已因 @DirtiesContext 关闭；本方法运行在全新 Context 中。
        // 原 Token（Redis 持久化）经 HTTP 应恢复会话。
        assertNotNull(sharedToken, "应复用第一 Context 登录的 Token");
        HttpResponse<String> me = get("/compat/auth/me", sharedToken);
        assertEquals(200, me.statusCode(), "第二 Context 中原 Token 应经 Redis 恢复会话");
        assertTrue(me.body().contains("sa-admin"), "恢复后的登录身份应一致");

        // ST-4/ST-8：注销后旧 Token 被拒绝（401）。
        HttpResponse<String> logout = post("/compat/auth/logout", sharedToken);
        assertEquals(200, logout.statusCode(), "注销应成功");
        HttpResponse<String> afterLogout = get("/compat/auth/me", sharedToken);
        assertEquals(401, afterLogout.statusCode(), "注销后旧 Token 应被 401 拒绝");
    }

    @Test
    @Order(3)
    @DisplayName("ST-3/ST-8：角色不足被拒绝（403）")
    void wrongRoleRejectedViaHttp() throws Exception {
        // 普通用户登录（无 admin 角色/权限）。
        String normalToken = login("sa-normal");
        assertNotNull(normalToken);

        // 身份读取可通过（仅需登录）。
        assertEquals(200, get("/compat/auth/me", normalToken).statusCode());

        // 角色不足：403。
        assertEquals(403, get("/compat/auth/role-check", normalToken).statusCode(),
                "角色不足应被 403 拒绝");

        // 权限不足：403。
        assertEquals(403, get("/compat/auth/permission-check", normalToken).statusCode(),
                "权限不足应被 403 拒绝");
    }

    /** 经 HTTP 登录并返回 Token。 */
    private String login(String name) throws Exception {
        HttpResponse<String> resp = post("/compat/auth/login?name=" + name, null);
        assertEquals(200, resp.statusCode(), "登录接口应返回 200");
        // 解析 {"token":"..."}（避免引入 JSON 依赖；响应结构由 Harness 控制）。
        Matcher matcher = Pattern.compile("\"token\":\"([^\"]+)\"").matcher(resp.body());
        assertTrue(matcher.find(), "登录响应应包含 token，实际: " + resp.body());
        return matcher.group(1);
    }

    /** 携带（或缺失）Token 的 GET 请求。 */
    private HttpResponse<String> get(String path, String token) throws Exception {
        return request("GET", path, token);
    }

    /** 携带（或缺失）Token 的 POST 请求。 */
    private HttpResponse<String> post(String path, String token) throws Exception {
        return request("POST", path, token);
    }

    /** 构造 HTTP 请求（含可选 satoken 头）。 */
    private HttpResponse<String> request(String method, String path, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + path))
                .timeout(java.time.Duration.ofSeconds(15));
        if ("POST".equals(method)) {
            builder.POST(HttpRequest.BodyPublishers.noBody());
        }
        if (token != null) {
            builder.header("satoken", token);
        }
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
}
