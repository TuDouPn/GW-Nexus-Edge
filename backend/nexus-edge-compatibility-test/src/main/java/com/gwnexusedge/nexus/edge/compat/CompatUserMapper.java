package com.gwnexusedge.nexus.edge.compat;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * MyBatis-Plus 兼容性验证 Mapper（MP-2：@MapperScan 扫描注册；MP-3：BaseMapper CRUD）。
 *
 * <p>继承官方 {@link BaseMapper}，由 {@code @MapperScan}（或 {@code @Mapper} 注解）
 * 注册为 Spring Bean；CRUD/分页经真实 MyBatis-Plus 执行链命中真实 MySQL。
 * 仅用于兼容性验证，非正式业务 Mapper。
 */
@Mapper
public interface CompatUserMapper extends BaseMapper<CompatUser> {
}
