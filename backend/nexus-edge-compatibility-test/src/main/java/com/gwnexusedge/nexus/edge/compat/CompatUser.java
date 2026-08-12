package com.gwnexusedge.nexus.edge.compat;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * MyBatis-Plus 兼容性验证实体（MP-3：BaseMapper 真实 CRUD）。
 *
 * <p>对应真实表 {@code compat_user}（由 Flyway MySQL 迁移创建，见
 * {@code db/migration/mysql/V1__create_compat_user.sql}）。仅用于兼容性验证，
 * 非正式业务实体。
 */
@TableName("compat_user")
public class CompatUser {

    /** 主键：数据库自增（真实 MySQL 身份生成）。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户名。 */
    private String name;

    /** 分数（基础类型映射验证字段）。 */
    private Integer score;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getScore() {
        return score;
    }

    public void setScore(Integer score) {
        this.score = score;
    }
}
