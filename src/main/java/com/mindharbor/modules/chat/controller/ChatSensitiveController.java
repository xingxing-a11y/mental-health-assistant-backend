package com.mindharbor.modules.chat.controller;

import com.mindharbor.common.ApiResponse;
import com.mindharbor.modules.chat.dto.SensitiveCheckRequest;
import com.mindharbor.modules.chat.service.SensitiveWordService;
import com.mindharbor.modules.chat.vo.SensitiveCheckVO;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 发送前敏感内容预校验。
 * 这里只负责给前端提示，对话主接口仍会执行同一套服务端强制校验。
 */
@RestController
@RequestMapping("/chat")
public class ChatSensitiveController {

    private static final Logger log = LoggerFactory.getLogger(ChatSensitiveController.class);

    private final SensitiveWordService service;

    public ChatSensitiveController(SensitiveWordService service) {
        this.service = service;
    }

    @PostMapping("/sensitive-check")
    public ApiResponse<SensitiveCheckVO> check(@Valid @RequestBody SensitiveCheckRequest request) {
        log.info("Pre-check message sensitivity");
        return ApiResponse.ok(service.inspect(request.getContent()));
    }
}
