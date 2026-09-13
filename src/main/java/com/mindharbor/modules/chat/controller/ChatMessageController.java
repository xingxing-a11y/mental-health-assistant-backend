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

    @PutMapping("/{messageId}/favorite")
    public ApiResponse<?> favorite(
            @PathVariable long messageId,
            @Valid @RequestBody FavoriteRequest request) {
        service.favoriteMessage(messageId, request.getFavorite());
        return ApiResponse.ok(null);
    }

    @GetMapping("/favorites")
    public ApiResponse<?> favorites() {
        return ApiResponse.ok(service.favoriteMessages());
    }
}
