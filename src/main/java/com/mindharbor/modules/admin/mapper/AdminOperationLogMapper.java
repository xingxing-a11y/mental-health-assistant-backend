package com.mindharbor.modules.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mindharbor.modules.admin.entity.AdminOperationLog;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 管理员操作日志数据访问层，语句写在 resources/mapper/admin/AdminOperationLogMapper.xml。 */
@Mapper
public interface AdminOperationLogMapper extends BaseMapper<AdminOperationLog> {

    List<AdminOperationLog> findPage(
            @Param("type") String type,
            @Param("startAt") Instant startAt,
            @Param("endAt") Instant endAt,
            @Param("limit") int limit,
            @Param("offset") int offset);

    long countPage(
            @Param("type") String type,
            @Param("startAt") Instant startAt,
            @Param("endAt") Instant endAt);
}