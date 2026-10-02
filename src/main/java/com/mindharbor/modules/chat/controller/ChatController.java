package com.mindharbor.modules.chat.controller;

import com.mindharbor.common.ApiResponse;
import com.mindharbor.modules.chat.dto.ChatMessageRequest;
import com.mindharbor.modules.chat.dto.ChatSettingsRequest;
import com.mindharbor.modules.chat.dto.CreateSessionRequest;
import com.mindharbor.modules.chat.entity.ChatMessage;
import com.mindharbor.modules.chat.service.ChatService;
import com.mindharbor.modules.chat.service.ChatExportService;
import com.mindharbor.modules.chat.vo.ChatExportFileVO;
import com.mindharbor.modules.chat.vo.ChatSessionVO;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** 会话接口：负责会话资源路由和 SSE 请求入口，业务逻辑在 service 层。 */
@RestController
@RequestMapping("/chat/sessions")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final ChatService chatService;

    private final ChatExportService chatExportService;

    public ChatController(ChatService chatService, ChatExportService chatExportService) {
        this.chatService = chatService;
        this.chatExportService = chatExportService;
    }

    @GetMapping
    public ApiResponse<List<ChatSessionVO>> list() {
        log.info("List chat sessions");
        return ApiResponse.ok(chatService.list());
    }

    @GetMapping("/archived")
    public ApiResponse<List<ChatSessionVO>> archived() {
        log.info("List archived chat sessions");
        return ApiResponse.ok(chatService.archived());
    }

    @PostMapping
    public ApiResponse<ChatSessionVO> create(
            @RequestBody(required = false) CreateSessionRequest request) {
        log.info("Create chat session");
        return ApiResponse.ok(chatService.create(request));
    }

    /** 查询会话历史消息。 */
    @GetMapping("/{sessionId}/messages")
    public ApiResponse<List<ChatMessage>> history(@PathVariable long sessionId) {
        log.info("Get chat history, sessionId={}", sessionId);
        return ApiResponse.ok(chatService.history(sessionId));
    }

    /** 永久删除已归档会话及其全部消息。 */
    @DeleteMapping("/{sessionId}")
    public ApiResponse<Void> delete(@PathVariable long sessionId) {
        log.info("Delete chat session, sessionId={}", sessionId);
        chatService.delete(sessionId);
        return ApiResponse.ok(null);
    }

    @PutMapping("/{sessionId}/name")
    public ApiResponse<ChatSessionVO> rename(
            @PathVariable long sessionId,
            @Valid @RequestBody CreateSessionRequest request) {
        log.info("Rename chat session, sessionId={}", sessionId);
        return ApiResponse.ok(chatService.rename(sessionId, request));
    }

    @PutMapping("/{sessionId}/pin")
    public ApiResponse<ChatSessionVO> togglePin(@PathVariable long sessionId) {
        log.info("Toggle chat session pin, sessionId={}", sessionId);
        return ApiResponse.ok(chatService.togglePin(sessionId));
    }

    @PutMapping("/{sessionId}/archive")
    public ApiResponse<ChatSessionVO> archive(@PathVariable long sessionId) {
        log.info("Archive chat session, sessionId={}", sessionId);
        return ApiResponse.ok(chatService.archive(sessionId));
    }

    @PutMapping("/{sessionId}/unarchive")
    public ApiResponse<ChatSessionVO> unarchive(@PathVariable long sessionId) {
        log.info("Unarchive chat session, sessionId={}", sessionId);
        return ApiResponse.ok(chatService.unarchive(sessionId));
    }

    /** 使用 POST 传递问题，避免原生 EventSource 无法携带请求体。 */
    @PostMapping(value = "/{sessionId}/messages", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(
            @PathVariable long sessionId,
            @Valid @RequestBody ChatMessageRequest request) {
        log.info("Start SSE chat, sessionId={}", sessionId);
        return chatService.stream(sessionId, request);
    }

    /**
     * 导出单次会话，format 支持 txt 与 pdf。
     * 导出属于重资源操作，并发超限时 service 会抛出 429。
     */
    @GetMapping("/{sessionId}/export")
    public ResponseEntity<byte[]> export(
            @PathVariable long sessionId,
            @RequestParam(defaultValue = "txt") String format) {
        log.info("Export chat session, sessionId={}, format={}", sessionId, format);
        ChatExportFileVO file = chatExportService.export(sessionId, format);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(file.getContentType()));
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(file.getFilename(), StandardCharsets.UTF_8)
                .build());
        headers.setContentLength(file.getContent().length);
        return new ResponseEntity<>(file.getContent(), headers, HttpStatus.OK);
    }

    @DeleteMapping("/{sessionId}/context")
    public ApiResponse<Void> clear(@PathVariable long sessionId) {
        log.info("Clear chat context, sessionId={}", sessionId);
        chatService.clearContext(sessionId);
        return ApiResponse.ok(null);
    }

    @PutMapping("/{sessionId}/settings")
    public ApiResponse<Void> settings(
            @PathVariable long sessionId,
            @Valid @RequestBody ChatSettingsRequest request) {
        log.info("Update chat session settings, sessionId={}", sessionId);
        chatService.updateSettings(sessionId, request);
        return ApiResponse.ok(null);
    }
}
