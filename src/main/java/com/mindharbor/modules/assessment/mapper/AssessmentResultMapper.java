package com.mindharbor.modules.assessment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mindharbor.modules.assessment.entity.AssessmentResult;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface AssessmentResultMapper extends BaseMapper<AssessmentResult> {
    @Select("SELECT * FROM assessment_results WHERE user_id = #{userId} ORDER BY created_at DESC")
    List<AssessmentResult> findByUserIdOrderByCreatedAtDesc(Long userId);
}
