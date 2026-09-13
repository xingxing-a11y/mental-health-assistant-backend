package com.mindharbor.modules.chat.mapper;

import com.mindharbor.modules.chat.entity.ChatSession;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatSessionMapper extends JpaRepository<ChatSession, Long> {

    List<ChatSession> findByUserIdOrderByLatestMessageAtDesc(Long userId);
}
