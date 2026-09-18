package com.mindharbor.modules.relax.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mindharbor.modules.relax.entity.RelaxRecord;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 放松练习记录数据访问层，语句写在 resources/mapper/relax/RelaxRecordMapper.xml。 */
@Mapper
public interface RelaxRecordMapper extends BaseMapper<RelaxRecord> {

    List<RelaxRecord> findByUserId(
            @Param("userId") Long userId,
            @Param("type") String type,
            @Param("limit") int limit,
            @Param("offset") int offset);

    long countByUserId(@Param("userId") Long userId, @Param("type") String type);

    long countSince(@Param("userId") Long userId, @Param("since") Instant since);
}