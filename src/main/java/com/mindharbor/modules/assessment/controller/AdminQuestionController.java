package com.mindharbor.modules.assessment.controller;

import com.mindharbor.common.ApiResponse;
import com.mindharbor.modules.assessment.dto.QuestionRequest;
import com.mindharbor.modules.assessment.service.AssessmentService;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/admin/scales")
public class AdminQuestionController {

    private static final Logger log = LoggerFactory.getLogger(AdminQuestionController.class);
    private final AssessmentService service;

    public AdminQuestionController(AssessmentService service) {
        this.service = service;
    }

    @PutMapping("/{scaleId}/questions")
    public ApiResponse<?> replace(
            @PathVariable Long scaleId,
            @Valid @Size(max = 200, message = "题目数量不能超过 200 个")
            @RequestBody List<@Valid QuestionRequest> requests) {
        log.info("Replace assessment questions, scaleId={}", scaleId);
        return ApiResponse.ok(service.replaceQuestions(scaleId, requests));
    }
}
