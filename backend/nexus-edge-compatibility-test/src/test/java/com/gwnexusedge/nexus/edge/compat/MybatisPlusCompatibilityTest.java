package com.gwnexusedge.nexus.edge.compat;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * DEV-0002 MyBatis-Plus 兼容性测试（§8.1：MP-1~MP-5）。
 *
 * <p>使用官方 Spring Boot 4 Starter（mybatis-plus-spring-boot4-starter 3.5.17）+
 * Testcontainers 真实 MySQL：验证 Spring 自动装配与 Mapper 扫描（MP-2）、
 * BaseMapper 真实 CRUD（MP-3）、事务提交与回滚（MP-4）、分页插件（MP-5）。
 * 数据表 compat_user 由 Flyway MySQL 迁移（application-mysql Profile）在真实 MySQL 创建。
 *
 * <p>禁止 Mock 数据库/H2/固定 JSON：全部断言命中真实 MySQL。
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("mysql")
class MybatisPlusCompatibilityTest {

    /** 固定版本 MySQL 镜像（JD-2：报告中记录镜像 Digest）。 */
    @Container
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4.5");

    /**
     * 把 Testcontainers MySQL 连接注入 Spring 上下文（真实数据源）。
     *
     * @param registry 动态属性注册器
     */
    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired
    CompatUserMapper mapper;

    @Autowired
    CompatUserService service;

    @Test
    @DisplayName("MP-2/MP-3：Mapper 扫描注册 + BaseMapper 真实 CRUD（真实 MySQL）")
    void mapperScanAndBaseMapperCrud() {
        assertNotNull(mapper, "Mapper 应经扫描注册为 Spring Bean");

        // 插入（真实 MySQL 自增主键）。
        CompatUser user = new CompatUser();
        user.setName("mp-crud-" + System.nanoTime());
        user.setScore(88);
        mapper.insert(user);
        assertNotNull(user.getId(), "自增主键应回填");

        // 查询。
        CompatUser loaded = mapper.selectById(user.getId());
        assertNotNull(loaded, "selectById 应命中真实数据");
        assertEquals(user.getName(), loaded.getName(), "name 应一致");
        assertEquals(88, loaded.getScore(), "score 应一致");

        // 更新。
        loaded.setScore(99);
        assertEquals(1, mapper.updateById(loaded), "updateById 应更新一行");
        assertEquals(99, mapper.selectById(user.getId()).getScore(), "更新应持久化");

        // 删除。
        assertEquals(1, mapper.deleteById(user.getId()), "deleteById 应删除一行");
        assertNull(mapper.selectById(user.getId()), "删除后不应再查到");
    }

    @Test
    @DisplayName("MP-4：事务提交与回滚（真实 MySQL @Transactional）")
    void transactionCommitAndRollback() {
        // 提交路径：方法正常返回后数据可见。
        CompatUser committed = new CompatUser();
        committed.setName("mp-commit-" + System.nanoTime());
        committed.setScore(10);
        service.createAndCommit(committed);
        assertNotNull(committed.getId(), "提交路径应回填主键");
        assertNotNull(mapper.selectById(committed.getId()), "提交后数据应可见");

        // 回滚路径：方法抛异常后数据不可见（真实回滚）。
        CompatUser rolledBack = new CompatUser();
        rolledBack.setName("mp-rollback-" + System.nanoTime());
        rolledBack.setScore(20);
        assertThrows(IllegalStateException.class, () -> service.createAndRollback(rolledBack),
                "回滚路径应抛出触发异常");
        assertNull(mapper.selectById(rolledBack.getId()), "回滚后数据应不可见");
    }

    @Test
    @DisplayName("MP-5：分页插件（total/records 来自真实数据库）")
    void paginationPluginWorks() {
        // 预置数据：清理 + 插入 3 行。
        mapper.delete(new LambdaQueryWrapper<CompatUser>().likeRight(CompatUser::getName, "mp-page-"));
        for (int i = 0; i < 3; i++) {
            CompatUser user = new CompatUser();
            user.setName("mp-page-" + i + "-" + System.nanoTime());
            user.setScore(i);
            mapper.insert(user);
        }

        // 分页查询（第 1 页，每页 2 条）。
        Page<CompatUser> page = mapper.selectPage(
                new Page<>(1, 2), new LambdaQueryWrapper<CompatUser>().likeRight(CompatUser::getName, "mp-page-"));
        assertEquals(3, page.getTotal(), "total 应为真实数据库统计");
        assertEquals(2, page.getRecords().size(), "records 应为当前页数据");
        assertEquals(2, page.getPages(), "pages 应正确计算（3 条 / 每页 2 条 = 2 页）");

        // 第 2 页：剩余 1 条。
        Page<CompatUser> page2 = mapper.selectPage(
                new Page<>(2, 2), new LambdaQueryWrapper<CompatUser>().likeRight(CompatUser::getName, "mp-page-"));
        assertEquals(1, page2.getRecords().size(), "第二页应返回剩余记录");

        // 清理。
        List<CompatUser> all = mapper.selectList(
                new LambdaQueryWrapper<CompatUser>().likeRight(CompatUser::getName, "mp-page-"));
        all.forEach(u -> mapper.deleteById(u.getId()));
    }
}
