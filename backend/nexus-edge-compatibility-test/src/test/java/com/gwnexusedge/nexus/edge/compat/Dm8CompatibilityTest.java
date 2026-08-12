package com.gwnexusedge.nexus.edge.compat;

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
 * DM8 兼容验证入口（仅 dm8-compat Profile 显式启用时运行；DEV-0002 评审 P1-7/P1-8）。
 *
 * <p>行为（严格区分能力边界）：
 * <ol>
 *   <li><b>驱动解析</b>：验证 DM8 官方驱动类 {@code dm.jdbc.driver.DmDriver} 可由
 *       dm8-compat Profile 的 {@code com.dameng:DmJdbcDriver18} 依赖解析（无需服务器）；</li>
 *   <li><b>fail-closed</b>：读取 {@code dm8.url/dm8.username/dm8.password}（系统属性）或
 *       {@code DM8_URL/DM8_USERNAME/DM8_PASSWORD}（环境变量）——Profile 显式启用但缺少
 *       endpoint/credential 时<b>测试失败</b>，禁止静默跳过或假通过；</li>
 *   <li><b>真实连接</b>（环境可用时）：DriverManager 连接 + 基础查询 {@code SELECT 1}。</li>
 * </ol>
 *
 * <p>能力边界声明（P1-8）：当前无 DM8 授权环境时，本 Profile <b>仅验证驱动解析</b>；
 * 不声称可运行与 MySQL/PostgreSQL 同等级的 MP-6/FW-7/JD 用例；G-02 保持 PARTIAL。
 * 默认 `clean verify` 不运行本测试（surefire excludes）；显式启用：
 * {@code ./mvnw -P dm8-compat -pl nexus-edge-compatibility-test test}。
 */
class Dm8CompatibilityTest {

    /** DM8 官方 JDBC 驱动类名。 */
    private static final String DM8_DRIVER_CLASS = "dm.jdbc.driver.DmDriver";

    @Test
    @DisplayName("DM8：驱动解析 + 真实连接（Profile 显式启用但缺 endpoint/credential 时 fail-closed）")
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
        String pass = firstNonBlank(System.getProperty("dm8.password"), System.getenv("DM8_PASSWORD"));
        if (url == null || user == null || pass == null) {
            fail("dm8-compat Profile 已显式启用但缺少 DM8 endpoint/credential"
                    + "（dm8.url/dm8.username/dm8.password 或 DM8_URL/DM8_USERNAME/DM8_PASSWORD）；"
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

    /** 返回首个非空白值（null 表示两者皆缺失）。 */
    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        return b;
    }
}
