package com.mindharbor.modules.assessment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mindharbor.modules.assessment.entity.AssessmentQuestion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface AssessmentQuestionMapper extends BaseMapper<AssessmentQuestion> {
    @Select("SELECT * FROM assessment_questions WHERE scale_id = #{scaleId} ORDER BY question_order ASC")
    List<AssessmentQuestion> findByScaleIdOrderByQuestionOrderAsc(Long scaleId);

    @Select("SELECT * FROM assessment_questions WHERE id = #{id} AND scale_id = #{scaleId} LIMIT 1")
    java.util.Optional<AssessmentQuestion> findByIdAndScaleId(
            @Param("id") Long id,
            @Param("scaleId") Long scaleId);
}
