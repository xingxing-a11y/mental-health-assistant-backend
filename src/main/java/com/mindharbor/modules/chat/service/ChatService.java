package com.mindharbor.modules.chat.service;

import com.mindharbor.modules.chat.dto.ChatMessageRequest;
import com.mindharbor.modules.chat.dto.CreateSessionRequest;
import com.mindharbor.modules.chat.dto.ChatSettingsRequest;
import com.mindharbor.modules.chat.vo.ChatSessionVO;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.List;

public interface ChatService {

    ChatSessionVO create(CreateSessionRequest request);

    List<ChatSessionVO> list();

    List<?> history(long sessionId);

    void delete(long sessionId);

    SseEmitter stream(long sessionId, ChatMessageRequest request);

    void clearContext(long sessionId);

    void updateSettings(long sessionId, ChatSettingsRequest request);

    void favoriteMessage(long messageId, boolean favorite);

    List<?> favoriteMessages();
}


