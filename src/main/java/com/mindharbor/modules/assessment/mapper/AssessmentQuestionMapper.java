package com.mindharbor.modules.assessment.mapper;

import com.mindharbor.modules.assessment.entity.AssessmentQuestion;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssessmentQuestionMapper extends JpaRepository<AssessmentQuestion, Long> {

    List<AssessmentQuestion> findByScaleIdOrderByQuestionOrderAsc(Long scaleId);

    java.util.Optional<AssessmentQuestion> findByIdAndScaleId(Long id, Long scaleId);
}
