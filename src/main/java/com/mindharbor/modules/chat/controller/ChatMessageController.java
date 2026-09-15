package com.mindharbor.modules.chat.controller;

import com.mindharbor.common.ApiResponse;
import com.mindharbor.modules.chat.dto.FavoriteRequest;
import com.mindharbor.modules.chat.service.ChatService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/chat/messages")
public class ChatMessageController {
    private final ChatService service;

    public ChatMessageController(ChatService service) {
        this.service = service;
    }

    /**
     * 收藏ai消息
     * @param messageId
     * @param request
     * @return
     */
    @PutMapping("/{messageId}/favorite")
    public ApiResponse<?> favorite(
            @PathVariable long messageId,
            @Valid @RequestBody FavoriteRequest request) {
        service.favoriteMessage(messageId, request.getFavorite());
        return ApiResponse.ok(null);
    }

    /**
     * 查询收藏消息
     * @return
     */
    @GetMapping("/favorites")
    public ApiResponse<?> favorites() {
        return ApiResponse.ok(service.favoriteMessages());
    }
}
