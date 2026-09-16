package com.mindharbor.modules.chat.controller;

import com.mindharbor.common.ApiResponse;
import com.mindharbor.modules.chat.dto.ChatMessageRequest;
import com.mindharbor.modules.chat.dto.CreateSessionRequest;
import com.mindharbor.modules.chat.dto.ChatSettingsRequest;
import com.mindharbor.modules.chat.service.ChatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/chat/sessions")
/** 会话接口：负责会话资源路由和 SSE 请求入口。 */
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final ChatService service;

    public ChatController(ChatService service) {
        this.service = service;
    }

    @GetMapping

    public ApiResponse<?> list() {
        log.info("List chat sessions");
        return ApiResponse.ok(service.list());
    }

    @GetMapping("/archived")
    public ApiResponse<?> archived() {
        log.info("List archived chat sessions");
        return ApiResponse.ok(service.archived());
    }

    @PostMapping

    public ApiResponse<?> create(@RequestBody(required = false) CreateSessionRequest request) {
        log.info("Create chat session");
        return ApiResponse.ok(service.create(request));
    }

    /**
     * 查询会话历史
     * @param sessionId
     * @return
     */
    @GetMapping("/{sessionId}/messages")
    public ApiResponse<?> history(@PathVariable long sessionId) {
        log.info("Get chat history, sessionId={}", sessionId);
        return ApiResponse.ok(service.history(sessionId));
    }

    /**
     * 删除会话
     * @param sessionId
     * @return
     */
    @DeleteMapping("/{sessionId}")
    public ApiResponse<?> delete(@PathVariable long sessionId) {
        log.info("Delete chat session, sessionId={}", sessionId);
        service.delete(sessionId);
        return ApiResponse.ok(null);
    }

    @PutMapping("/{sessionId}/name")
    public ApiResponse<?> rename(
            @PathVariable long sessionId,
            @Valid @RequestBody CreateSessionRequest request) {
        return ApiResponse.ok(service.rename(sessionId, request));
    }

    @PutMapping("/{sessionId}/pin")
    public ApiResponse<?> togglePin(@PathVariable long sessionId) {
        return ApiResponse.ok(service.togglePin(sessionId));
    }

    @PutMapping("/{sessionId}/archive")
    public ApiResponse<?> archive(@PathVariable long sessionId) {
        return ApiResponse.ok(service.archive(sessionId));
    }

    @PutMapping("/{sessionId}/unarchive")
    public ApiResponse<?> unarchive(@PathVariable long sessionId) {
        return ApiResponse.ok(service.unarchive(sessionId));
    }

    /**
     * SSE 流式发送 AI 消息
     * @param sessionId
     * @param request
     * @return
     */
    @PostMapping(value = "/{sessionId}/messages", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    /** 使用 POST 传递问题，避免原生 EventSource 无法携带请求体和 Bearer Header。 */

    public SseEmitter stream(
            @PathVariable long sessionId,
            @Valid @RequestBody ChatMessageRequest request) {
        log.info("Start SSE chat, sessionId={}", sessionId);
        return service.stream(sessionId, request);
    }

    @DeleteMapping("/{sessionId}/context")

    public ApiResponse<?> clear(@PathVariable long sessionId) {
        log.info("Clear chat context, sessionId={}", sessionId);
        service.clearContext(sessionId);
        return ApiResponse.ok(null);
    }

    @PutMapping("/{sessionId}/settings")
    public ApiResponse<?> settings(
            @PathVariable long sessionId,
            @Valid @RequestBody ChatSettingsRequest request) {
        service.updateSettings(sessionId, request);
        return ApiResponse.ok(null);
    }

}
