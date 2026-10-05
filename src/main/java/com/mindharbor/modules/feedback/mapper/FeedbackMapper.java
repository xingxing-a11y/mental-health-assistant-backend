package com.mindharbor.modules.feedback.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mindharbor.modules.feedback.entity.Feedback;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 反馈工单数据访问层，语句写在 resources/mapper/feedback/FeedbackMapper.xml。 */
@Mapper
public interface FeedbackMapper extends BaseMapper<Feedback> {

    List<Feedback> findPage(
            @Param("status") String status,
            @Param("limit") int limit,
            @Param("offset") long offset);

    long countPage(@Param("status") String status);
}