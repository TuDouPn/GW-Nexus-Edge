package com.gwnexusedge.nexus.edge.compat;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 兼容性验证事务服务（MP-4：事务提交与回滚）。
 *
 * <p>提供两条真实事务路径：
 * <ul>
 *   <li>{@link #createAndCommit}：{@code @Transactional} 提交路径——方法正常返回后数据可见；</li>
 *   <li>{@link #createAndRollback}：{@code @Transactional} 回滚路径——方法内抛异常，
 *       真实数据库回滚，数据不可见。</li>
 * </ul>
 * 依赖 MyBatis-Plus Mapper，仅在 mysql Profile（含 DataSource）上下文装配；
 * 无数据库的 Test Slice（如 Sa-Token 仅 Redis 上下文）不创建本 Bean（避免缺 Mapper 失败）。
 * 仅用于兼容性验证，非正式业务服务。
 */
@Service
@Profile("mysql")
public class CompatUserService {

    private final CompatUserMapper mapper;

    public CompatUserService(CompatUserMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 事务提交路径：插入用户并正常返回。
     *
     * @param user 待插入用户
     */
    @Transactional
    public void createAndCommit(CompatUser user) {
        mapper.insert(user);
    }

    /**
     * 事务回滚路径：插入用户后抛出异常，验证真实回滚。
     *
     * @param user 待插入用户
     * @throws IllegalStateException 触发事务回滚
     */
    @Transactional
    public void createAndRollback(CompatUser user) {
        mapper.insert(user);
        throw new IllegalStateException("兼容性验证：触发事务回滚");
    }
}
