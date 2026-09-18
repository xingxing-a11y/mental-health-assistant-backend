package com.mindharbor.modules.assessment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mindharbor.modules.assessment.entity.AssessmentScale;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import java.util.List;
import java.util.Optional;

@Mapper
public interface AssessmentScaleMapper extends BaseMapper<AssessmentScale> {

    @Select("SELECT * FROM assessment_scales")
    List<AssessmentScale> findAll();

    @Select("SELECT * FROM assessment_scales WHERE published = 1")
    List<AssessmentScale> findAllPublished();

    @Select("SELECT * FROM assessment_scales WHERE id = #{id} LIMIT 1")
    Optional<AssessmentScale> findById(Long id);

}
