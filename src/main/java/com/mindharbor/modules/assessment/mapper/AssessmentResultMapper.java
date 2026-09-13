package com.mindharbor.modules.assessment.mapper;

import com.mindharbor.modules.assessment.entity.AssessmentResult;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssessmentResultMapper extends JpaRepository<AssessmentResult, Long> {

    List<AssessmentResult> findByUserIdOrderByCreatedAtDesc(Long userId);
}
