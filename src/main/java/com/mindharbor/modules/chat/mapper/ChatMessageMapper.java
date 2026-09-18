package com.mindharbor.modules.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mindharbor.modules.chat.entity.ChatMessage;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 消息数据访问层，语句写在 resources/mapper/chat/ChatMessageMapper.xml。 */
@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {

    Optional<ChatMessage> findById(@Param("id") Long id);

    List<ChatMessage> findBySessionIdOrderByCreatedAtAsc(@Param("sessionId") Long sessionId);

    int deleteBySessionId(@Param("sessionId") Long sessionId);

    int deleteExpiredBatch(
            @Param("userId") Long userId,
            @Param("before") Instant before,
            @Param("limit") int limit);

    List<ChatMessage> findFavorites(
            @Param("userId") Long userId,
            @Param("sessionId") Long sessionId,
            @Param("keyword") String keyword,
            @Param("limit") int limit,
            @Param("offset") int offset);

    long countFavorites(
            @Param("userId") Long userId,
            @Param("sessionId") Long sessionId,
            @Param("keyword") String keyword);
}