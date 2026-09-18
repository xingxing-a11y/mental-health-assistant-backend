package com.mindharbor.modules.relax.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mindharbor.modules.relax.entity.RelaxRecord;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 放松练习记录数据访问层，只声明查询语句，不承载业务判断。 */
@Mapper
public interface RelaxRecordMapper extends BaseMapper<RelaxRecord> {

    @Select("<script>"
            + "SELECT * FROM relax_records WHERE user_id = #{userId} "
            + "<if test='type != null'>AND type = #{type} </if>"
            + "ORDER BY created_at DESC LIMIT #{limit} OFFSET #{offset}"
            + "</script>")
    List<RelaxRecord> findByUserId(
            @Param("userId") Long userId,
            @Param("type") String type,
            @Param("limit") int limit,
            @Param("offset") int offset);

    @Select("<script>"
            + "SELECT COUNT(*) FROM relax_records WHERE user_id = #{userId} "
            + "<if test='type != null'>AND type = #{type}</if>"
            + "</script>")
    long countByUserId(@Param("userId") Long userId, @Param("type") String type);

    @Select("SELECT COUNT(*) FROM relax_records WHERE user_id = #{userId} AND created_at >= #{since}")
    long countSince(@Param("userId") Long userId, @Param("since") Instant since);
}
