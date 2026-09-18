package com.mindharbor.modules.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mindharbor.modules.admin.entity.AdminOperationLog;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 管理员操作日志数据访问层，条件过滤在 SQL 内完成，参数校验放在 service 层。 */
@Mapper
public interface AdminOperationLogMapper extends BaseMapper<AdminOperationLog> {

    @Select("<script>"
            + "SELECT * FROM admin_operation_logs "
            + "<where>"
            + "<if test='type != null'>AND type = #{type} </if>"
            + "<if test='startAt != null'>AND created_at &gt;= #{startAt} </if>"
            + "<if test='endAt != null'>AND created_at &lt;= #{endAt} </if>"
            + "</where>"
            + "ORDER BY created_at DESC, id DESC "
            + "LIMIT #{limit} OFFSET #{offset}"
            + "</script>")
    List<AdminOperationLog> findPage(
            @Param("type") String type,
            @Param("startAt") Instant startAt,
            @Param("endAt") Instant endAt,
            @Param("limit") int limit,
            @Param("offset") int offset);

    @Select("<script>"
            + "SELECT COUNT(*) FROM admin_operation_logs "
            + "<where>"
            + "<if test='type != null'>AND type = #{type} </if>"
            + "<if test='startAt != null'>AND created_at &gt;= #{startAt} </if>"
            + "<if test='endAt != null'>AND created_at &lt;= #{endAt} </if>"
            + "</where>"
            + "</script>")
    long countPage(
            @Param("type") String type,
            @Param("startAt") Instant startAt,
            @Param("endAt") Instant endAt);
}
