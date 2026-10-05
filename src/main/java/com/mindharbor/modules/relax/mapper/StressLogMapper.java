package com.mindharbor.modules.relax.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mindharbor.modules.relax.entity.StressLog;
import com.mindharbor.modules.relax.vo.StressTrendPointVO;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 压力打卡数据访问层，只声明查询语句，不承载业务判断。 */
@Mapper
public interface StressLogMapper extends BaseMapper<StressLog> {

    @Select("SELECT * FROM stress_logs WHERE user_id = #{userId} "
            + "ORDER BY created_at DESC LIMIT #{limit} OFFSET #{offset}")
    List<StressLog> findByUserId(
            @Param("userId") Long userId,
            @Param("limit") int limit,
            @Param("offset") long offset);

    @Select("SELECT COUNT(*) FROM stress_logs WHERE user_id = #{userId}")
    long countByUserId(@Param("userId") Long userId);

    @Select("SELECT * FROM stress_logs WHERE user_id = #{userId} ORDER BY created_at DESC LIMIT 1")
    Optional<StressLog> findLatestByUserId(@Param("userId") Long userId);

    @Select("SELECT AVG(level) FROM stress_logs WHERE user_id = #{userId} AND created_at >= #{since}")
    Double averageLevelSince(@Param("userId") Long userId, @Param("since") Instant since);

    @Select("SELECT DATE(created_at) AS log_date, AVG(level) AS average_level "
            + "FROM stress_logs WHERE user_id = #{userId} AND created_at >= #{since} "
            + "GROUP BY DATE(created_at) ORDER BY log_date ASC")
    List<StressTrendPointVO> findTrendSince(
            @Param("userId") Long userId,
            @Param("since") Instant since);
}
