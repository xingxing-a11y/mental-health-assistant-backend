package com.mindharbor.modules.feedback.controller;

import com.mindharbor.common.ApiResponse;
import com.mindharbor.common.PageResult;
import com.mindharbor.modules.feedback.dto.FeedbackHandleRequest;
import com.mindharbor.modules.feedback.service.FeedbackService;
import com.mindharbor.modules.feedback.vo.FeedbackVO;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 管理员反馈工单接口。 */
@RestController
@RequestMapping("/admin/feedbacks")
public class AdminFeedbackController {

    private static final Logger log = LoggerFactory.getLogger(AdminFeedbackController.class);

    private final FeedbackService feedbackService;

    public AdminFeedbackController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @GetMapping
    public ApiResponse<PageResult<FeedbackVO>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status) {
        log.info("List feedback tickets, page={}, size={}", page, size);
        return ApiResponse.ok(feedbackService.list(page, size, status));
    }

    @PutMapping("/{id}")
    public ApiResponse<FeedbackVO> handle(
            @PathVariable long id,
            @Valid @RequestBody FeedbackHandleRequest request) {
        log.info("Handle feedback ticket, feedbackId={}", id);
        return ApiResponse.ok(feedbackService.handle(id, request));
    }
}
