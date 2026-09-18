package com.mindharbor.modules.feedback.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mindharbor.modules.feedback.entity.Feedback;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 反馈工单数据访问层，状态过滤在 SQL 内完成，状态流转判断放在 service 层。 */
@Mapper
public interface FeedbackMapper extends BaseMapper<Feedback> {

    @Select("<script>"
            + "SELECT * FROM feedbacks "
            + "<if test='status != null'>WHERE status = #{status} </if>"
            + "ORDER BY created_at DESC, id DESC "
            + "LIMIT #{limit} OFFSET #{offset}"
            + "</script>")
    List<Feedback> findPage(
            @Param("status") String status,
            @Param("limit") int limit,
            @Param("offset") int offset);

    @Select("<script>"
            + "SELECT COUNT(*) FROM feedbacks "
            + "<if test='status != null'>WHERE status = #{status} </if>"
            + "</script>")
    long countPage(@Param("status") String status);
}
