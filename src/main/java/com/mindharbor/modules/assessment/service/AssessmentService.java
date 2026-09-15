package com.mindharbor.modules.assessment.service;

import com.mindharbor.modules.assessment.dto.AssessmentSubmitRequest;
import com.mindharbor.modules.assessment.dto.ScaleRequest;
import com.mindharbor.modules.assessment.dto.QuestionRequest;
import com.mindharbor.modules.assessment.entity.AssessmentScale;
import com.mindharbor.modules.assessment.entity.AssessmentQuestion;
import java.util.List;

public interface AssessmentService {

    List<?> scales();

    Object detail(Long scaleId);

    AssessmentScale create(ScaleRequest request);

    AssessmentScale update(Long scaleId, ScaleRequest request);

    void delete(Long scaleId);

    List<AssessmentQuestion> replaceQuestions(Long scaleId, List<QuestionRequest> requests);

    Object submit(AssessmentSubmitRequest request);

    Object analyze(Long resultId);

    List<?> history();

}

