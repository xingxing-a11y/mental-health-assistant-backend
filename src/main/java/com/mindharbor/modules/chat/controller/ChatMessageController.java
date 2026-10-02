package com.mindharbor.modules.chat.controller;

import com.mindharbor.common.ApiResponse;
import com.mindharbor.common.PageResult;
import com.mindharbor.modules.chat.dto.FavoriteRequest;
import com.mindharbor.modules.chat.service.ChatService;
import com.mindharbor.modules.chat.vo.FavoriteMessageVO;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 消息收藏接口。 */
@RestController
@RequestMapping("/chat/messages")
public class ChatMessageController {

    private static final Logger log = LoggerFactory.getLogger(ChatMessageController.class);

    private final ChatService chatService;

    public ChatMessageController(ChatService chatService) {
        this.chatService = chatService;
    }

    /** 收藏或取消收藏 AI 消息。 */
    @PutMapping("/{messageId}/favorite")
    public ApiResponse<Void> favorite(
            @PathVariable long messageId,
            @Valid @RequestBody FavoriteRequest request) {
        log.info("Update favorite state, messageId={}", messageId);
        chatService.favoriteMessage(messageId, request.getFavorite());
        return ApiResponse.ok(null);
    }

    /** 收藏页面取消收藏，与收藏列表使用同一套分页信封结构。 */
    @DeleteMapping("/favorites/{messageId}")
    public ApiResponse<Void> removeFavorite(@PathVariable long messageId) {
        log.info("Remove favorite message, messageId={}", messageId);
        chatService.removeFavorite(messageId);
        return ApiResponse.ok(null);
    }

    /** 分页查询当前用户收藏的 AI 消息，支持按会话与关键字过滤。 */
    @GetMapping("/favorites")
    public ApiResponse<PageResult<FavoriteMessageVO>> favorites(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long sessionId) {
        log.info("List favorite messages, page={}, size={}", page, size);
        return ApiResponse.ok(chatService.favoriteMessages(page, size, keyword, sessionId));
    }
}
