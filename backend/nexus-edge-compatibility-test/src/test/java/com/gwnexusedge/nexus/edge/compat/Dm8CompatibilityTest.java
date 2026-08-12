package com.gwnexusedge.nexus.edge.compat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * DM8 兼容验证入口（仅 dm8-compat Profile 显式启用时运行；DEV-0002 评审 P1-7/P1-8/P1-13）。
 *
 * <p>行为（严格区分能力边界）：
 * <ol>
 *   <li><b>驱动解析</b>：验证 DM8 官方驱动类 {@code dm.jdbc.driver.DmDriver} 可由
 *       dm8-compat Profile 的 {@code com.dameng:DmJdbcDriver18} 依赖解析（无需服务器）；</li>
 *   <li><b>fail-closed</b>：读取 DM8 连接配置——{@code dm8.url/dm8.username}（系统属性或环境变量）
 *       与<b>密码</b>（<b>只允许经环境 Secret {@code DM8_PASSWORD} 或受限 Secret 文件
 *       {@code DM8_PASSWORD_FILE} 注入，禁止 {@code -Ddm8.password} 命令行参数</b>，评审 P1-13）；
 *       Profile 显式启用但缺少 endpoint/credential 时<b>测试失败</b>，禁止静默跳过或假通过；</li>
 *   <li><b>真实连接</b>（环境可用时）：DriverManager 连接 + 基础查询 {@code SELECT 1}。</li>
 * </ol>
 *
 * <p>错误与日志<b>不输出 Secret Value</b>（fail 消息只引用配置名，不引用密码值，P1-13）。
 * 能力边界（P1-8）：当前无 DM8 授权环境时，本 Profile <b>仅验证驱动解析</b>；不声称可运行
 * 同等级 MP-6/FW-7/JD 用例；G-02 保持 PARTIAL。
 * 默认 `clean verify` 不运行本测试（surefire excludes）；显式启用：
 * {@code ./mvnw -P dm8-compat -pl nexus-edge-compatibility-test test}。
 */
class Dm8CompatibilityTest {

    /** DM8 官方 JDBC 驱动类名。 */
    private static final String DM8_DRIVER_CLASS = "dm.jdbc.driver.DmDriver";

    @Test
    @DisplayName("DM8：驱动解析 + 真实连接（缺 endpoint/credential 时 fail-closed；密码仅环境/Secret 文件）")
    void dm8DriverResolvesAndConnects() throws Exception {
        // ① 驱动解析（无需服务器；dm8-compat Profile 必须提供 DmJdbcDriver18 依赖）。
        try {
            Class.forName(DM8_DRIVER_CLASS);
        } catch (ClassNotFoundException e) {
            fail("DM8 官方驱动类 " + DM8_DRIVER_CLASS + " 不可解析——"
                    + "dm8-compat Profile 依赖未生效或驱动缺失（com.dameng:DmJdbcDriver18）");
        }

        // ② fail-closed：读取 DM8 endpoint/credential，缺失即失败（不静默跳过/假通过）。
        String url = firstNonBlank(System.getProperty("dm8.url"), System.getenv("DM8_URL"));
        String user = firstNonBlank(System.getProperty("dm8.username"), System.getenv("DM8_USERNAME"));
        String pass = resolveDm8Password(); // 仅环境 Secret / 受限 Secret 文件（P1-13）
        if (url == null || user == null || pass == null) {
            fail("dm8-compat Profile 已显式启用但缺少 DM8 endpoint/credential"
                    + "（dm8.url/dm8.username 或 DM8_URL/DM8_USERNAME；密码仅允许 DM8_PASSWORD 环境"
                    + " Secret 或 DM8_PASSWORD_FILE 受限 Secret 文件，禁止 -Ddm8.password 命令行参数）；"
                    + "DM8 兼容验证 fail-closed，禁止静默跳过或假通过");
        }

        // ③ 真实连接 + 基础查询（环境可用时；当前无 DM8 授权环境则不会到达此步）。
        try (Connection conn = DriverManager.getConnection(url, user, pass);
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT 1 AS OK")) {
            assertTrue(rs.next(), "DM8 基础查询应返回结果行");
            assertEquals(1, rs.getInt(1), "DM8 SELECT 1 应返回 1");
        }
    }

    /**
     * 解析 DM8 密码（评审 P1-13）：<b>禁止 {@code -Ddm8.password} 命令行参数</b>。
     *
     * <p>优先级：
     * <ol>
     *   <li>环境 Secret {@code DM8_PASSWORD}（直接注入值）；</li>
     *   <li>受限 Secret 文件 {@code DM8_PASSWORD_FILE}（路径指向仅所有者可读的文件；
     *       读取首行并去除行尾换行）；</li>
     * </ol>
     * 两者皆缺返回 null（触发 fail-closed）。错误/日志不输出 Secret Value。
     *
     * @return 密码值（经环境/受限文件注入）或 null
     * @throws IOException Secret 文件读取失败（fail-closed）
     */
    private static String resolveDm8Password() throws IOException {
        String envValue = System.getenv("DM8_PASSWORD");
        if (envValue != null && !envValue.isBlank()) {
            return envValue;
        }
        String filePath = System.getenv("DM8_PASSWORD_FILE");
        if (filePath != null && !filePath.isBlank()) {
            Path file = Path.of(filePath);
            if (!Files.isRegularFile(file)) {
                fail("DM8_PASSWORD_FILE 指向的 Secret 文件不存在: " + filePath + "（fail-closed）");
            }
            String content = Files.readString(file, StandardCharsets.UTF_8).strip();
            if (content.isBlank()) {
                fail("DM8_PASSWORD_FILE 指向的 Secret 文件为空（fail-closed）");
            }
            return content;
        }
        return null;
    }

    /** 返回首个非空白值（null 表示两者皆缺失）。 */
    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        return b;
    }
}
