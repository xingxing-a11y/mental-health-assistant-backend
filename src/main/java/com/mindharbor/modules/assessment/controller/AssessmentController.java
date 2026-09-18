package com.mindharbor.modules.assessment.controller;

import com.mindharbor.common.ApiResponse;
import com.mindharbor.modules.assessment.dto.AssessmentAnalysisRequest;
import com.mindharbor.modules.assessment.dto.AssessmentSubmitRequest;
import com.mindharbor.modules.assessment.dto.FavoriteScaleRequest;
import com.mindharbor.modules.assessment.service.AssessmentService;
import com.mindharbor.modules.assessment.vo.AssessmentAnalysisVO;
import com.mindharbor.modules.assessment.vo.AssessmentHistoryVO;
import com.mindharbor.modules.assessment.vo.AssessmentComparisonVO;
import com.mindharbor.modules.assessment.vo.AssessmentFavoriteVO;
import com.mindharbor.modules.assessment.vo.AssessmentRecommendationVO;
import com.mindharbor.modules.assessment.vo.AssessmentReportVO;
import com.mindharbor.modules.assessment.vo.AssessmentScaleVO;
import com.mindharbor.modules.assessment.vo.AssessmentScaleDetailVO;
import jakarta.validation.Valid;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 心理测评接口，分值与报告计算全部由 service 层完成。 */
@RestController
@RequestMapping("/assessments")
public class AssessmentController {

    private static final Logger log = LoggerFactory.getLogger(AssessmentController.class);

    private final AssessmentService service;

    public AssessmentController(AssessmentService service) {
        this.service = service;
    }

    /** 量表列表对匿名访问同样开放，登录后额外返回收藏状态。 */
    @GetMapping("/scales")
    public ApiResponse<List<AssessmentScaleVO>> scales() {
        log.info("List assessment scales");
        return ApiResponse.ok(service.publishedScales());
    }

    @GetMapping("/scales/{scaleId}")
    public ApiResponse<AssessmentScaleDetailVO> detail(@PathVariable Long scaleId) {
        log.info("Get assessment scale, scaleId={}", scaleId);
        return ApiResponse.ok(service.detail(scaleId));
    }

    @PostMapping
    public ApiResponse<AssessmentReportVO> submit(@RequestBody AssessmentSubmitRequest request) {
        log.info("Submit assessment, scaleId={}", request.getScaleId());
        return ApiResponse.ok(service.submit(request));
    }

    @PostMapping("/analysis")
    public ApiResponse<AssessmentAnalysisVO> analyze(
            @Valid @RequestBody AssessmentAnalysisRequest request) {
        log.info("Analyze assessment result, resultId={}", request.getResultId());
        return ApiResponse.ok(service.analyze(request.getResultId()));
    }

    @GetMapping
    public ApiResponse<List<AssessmentHistoryVO>> history() {
        log.info("List assessment history");
        return ApiResponse.ok(service.history());
    }

    /** 同一量表的历次结果，供结果对比页展示分数变化。 */
    @GetMapping("/scales/{scaleId}/history")
    public ApiResponse<List<AssessmentComparisonVO>> scaleHistory(@PathVariable Long scaleId) {
        log.info("List scale history, scaleId={}", scaleId);
        return ApiResponse.ok(service.scaleHistory(scaleId));
    }

    @GetMapping("/favorites")
    public ApiResponse<List<AssessmentFavoriteVO>> favorites() {
        log.info("List favorite scales");
        return ApiResponse.ok(service.favorites());
    }

    @PutMapping("/scales/{scaleId}/favorite")
    public ApiResponse<Void> updateFavorite(
            @PathVariable Long scaleId,
            @Valid @RequestBody FavoriteScaleRequest request) {
        log.info("Update scale favorite, scaleId={}", scaleId);
        service.updateFavorite(scaleId, Boolean.TRUE.equals(request.getFavorite()));
        return ApiResponse.ok(null);
    }

    @GetMapping("/recommendations")
    public ApiResponse<List<AssessmentRecommendationVO>> recommendations() {
        log.info("List recommended scales");
        return ApiResponse.ok(service.recommendations());
    }
}
