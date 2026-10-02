package com.mindharbor.modules.assessment.controller;

import com.mindharbor.common.ApiResponse;
import com.mindharbor.modules.assessment.dto.ScaleRequest;
import com.mindharbor.modules.assessment.entity.AssessmentScale;
import com.mindharbor.modules.assessment.service.AssessmentService;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/admin/scales")
public class AdminAssessmentController {

    private static final Logger log = LoggerFactory.getLogger(AdminAssessmentController.class);
    private final AssessmentService assessmentService;

    public AdminAssessmentController(AssessmentService assessmentService) {
        this.assessmentService = assessmentService;
    }

    @GetMapping
    public ApiResponse<List<AssessmentScale>> list() {
        log.info("List scales for administrator");
        return ApiResponse.ok(assessmentService.scales());
    }

    @PostMapping
    public ApiResponse<AssessmentScale> create(@Valid @RequestBody ScaleRequest request) {
        log.info("Create assessment scale");
        return ApiResponse.ok(assessmentService.create(request));
    }

    @PutMapping("/{scaleId}")
    public ApiResponse<AssessmentScale> update(
            @PathVariable Long scaleId,
            @Valid @RequestBody ScaleRequest request) {
        log.info("Update assessment scale, scaleId={}", scaleId);
        return ApiResponse.ok(assessmentService.update(scaleId, request));
    }

    @PutMapping("/{scaleId}/publish")
    public ApiResponse<AssessmentScale> publish(@PathVariable Long scaleId) {
        log.info("Publish assessment scale, scaleId={}", scaleId);
        return ApiResponse.ok(assessmentService.publish(scaleId));
    }

    @DeleteMapping("/{scaleId}")
    public ApiResponse<Void> delete(@PathVariable Long scaleId) {
        log.info("Delete assessment scale, scaleId={}", scaleId);
        assessmentService.delete(scaleId);
        return ApiResponse.ok(null);
    }
}
