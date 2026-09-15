package com.mindharbor.modules.assessment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mindharbor.modules.assessment.entity.AssessmentOption;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Delete;

@Mapper
public interface AssessmentOptionMapper extends BaseMapper<AssessmentOption> {

    @Select("SELECT * FROM assessment_options WHERE question_id = #{questionId} ORDER BY id ASC")
    List<AssessmentOption> findByQuestionId(Long questionId);

    @Delete("DELETE FROM assessment_options WHERE question_id = #{questionId}")
    int deleteByQuestionId(Long questionId);
}
