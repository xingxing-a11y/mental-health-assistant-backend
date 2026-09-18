package com.mindharbor.modules.assessment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mindharbor.modules.assessment.entity.AssessmentQuestion;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 测评题目数据访问层，只声明查询语句，不承载业务判断。 */
@Mapper
public interface AssessmentQuestionMapper extends BaseMapper<AssessmentQuestion> {

    @Select("SELECT * FROM assessment_questions WHERE scale_id = #{scaleId} "
            + "ORDER BY question_order ASC")
    List<AssessmentQuestion> findByScaleIdOrderByQuestionOrderAsc(Long scaleId);

    @Select("SELECT * FROM assessment_questions WHERE id = #{id} AND scale_id = #{scaleId} LIMIT 1")
    Optional<AssessmentQuestion> findByIdAndScaleId(
            @Param("id") Long id,
            @Param("scaleId") Long scaleId);

    @Delete("DELETE FROM assessment_questions WHERE scale_id = #{scaleId}")
    int deleteByScaleId(Long scaleId);
}
