package com.mindharbor.modules.chat.service;

import com.mindharbor.common.PageResult;
import com.mindharbor.modules.chat.dto.ChatMessageRequest;
import com.mindharbor.modules.chat.dto.ChatSettingsRequest;
import com.mindharbor.modules.chat.dto.CreateSessionRequest;
import com.mindharbor.modules.chat.entity.ChatMessage;
import com.mindharbor.modules.chat.vo.ChatSessionVO;
import com.mindharbor.modules.chat.vo.FavoriteMessageVO;
import java.util.List;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** 会话与消息业务接口。 */
public interface ChatService {

    ChatSessionVO create(CreateSessionRequest request);

    List<ChatSessionVO> list();

    List<ChatSessionVO> archived();

    List<ChatMessage> history(long sessionId);

    void delete(long sessionId);

    SseEmitter stream(long sessionId, ChatMessageRequest request);

    void clearContext(long sessionId);

    void updateSettings(long sessionId, ChatSettingsRequest request);

    ChatSessionVO rename(long sessionId, CreateSessionRequest request);

    ChatSessionVO togglePin(long sessionId);

    ChatSessionVO archive(long sessionId);

    ChatSessionVO unarchive(long sessionId);

    void favoriteMessage(long messageId, boolean favorite);

    void removeFavorite(long messageId);

    PageResult<FavoriteMessageVO> favoriteMessages(int page, int size, String keyword, Long sessionId);
}
