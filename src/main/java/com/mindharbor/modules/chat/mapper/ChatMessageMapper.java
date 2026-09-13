package com.mindharbor.modules.chat.mapper;

import com.mindharbor.modules.chat.entity.ChatMessage;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatMessageMapper extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findBySessionIdOrderByCreatedAtAsc(Long sessionId);

    void deleteBySessionId(Long sessionId);

    List<ChatMessage> findBySessionUserIdAndFavoriteTrueOrderByCreatedAtDesc(Long userId);
}
