package com.mindharbor.modules.assessment.controller;

import com.mindharbor.common.ApiResponse;
import com.mindharbor.modules.assessment.dto.AssessmentSubmitRequest;
import com.mindharbor.modules.assessment.dto.AssessmentAnalysisRequest;
import com.mindharbor.modules.assessment.service.AssessmentService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/assessments")
public class AssessmentController {

    private static final Logger log = LoggerFactory.getLogger(AssessmentController.class);

    private final AssessmentService service;

    public AssessmentController(AssessmentService service) {
        this.service = service;
    }

    @GetMapping("/scales")

    public ApiResponse<?> scales() {
        log.info("List assessment scales");
        return ApiResponse.ok(service.scales());
    }

    @GetMapping("/scales/{scaleId}")
    public ApiResponse<?> detail(@PathVariable Long scaleId) {
        log.info("Get assessment scale, scaleId={}", scaleId);
        return ApiResponse.ok(service.detail(scaleId));
    }

    @PostMapping

    public ApiResponse<?> submit(@RequestBody AssessmentSubmitRequest request) {
        log.info("Submit assessment, scaleId={}", request.getScaleId());
        return ApiResponse.ok(service.submit(request));
    }

    @PostMapping("/analysis")
    public ApiResponse<?> analyze(@Valid @RequestBody AssessmentAnalysisRequest request) {
        log.info("Analyze assessment result, resultId={}", request.getResultId());
        return ApiResponse.ok(service.analyze(request.getResultId()));
    }

    @GetMapping
    public ApiResponse<?> history() {
        log.info("List assessment history");
        return ApiResponse.ok(service.history());
    }
}


