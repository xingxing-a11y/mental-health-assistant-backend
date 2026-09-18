package com.mindharbor.modules.feedback.controller;

import com.mindharbor.common.ApiResponse;
import com.mindharbor.modules.feedback.dto.FeedbackRequest;
import com.mindharbor.modules.feedback.service.FeedbackService;
import com.mindharbor.modules.feedback.vo.FeedbackVO;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 用户反馈提交接口。 */
@RestController
@RequestMapping("/feedbacks")
public class FeedbackController {

    private static final Logger log = LoggerFactory.getLogger(FeedbackController.class);

    private final FeedbackService service;

    public FeedbackController(FeedbackService service) {
        this.service = service;
    }

    @PostMapping
    public ApiResponse<FeedbackVO> submit(@Valid @RequestBody FeedbackRequest request) {
        log.info("Submit user feedback, type={}", request.getType());
        return ApiResponse.ok(service.submit(request));
    }
}
