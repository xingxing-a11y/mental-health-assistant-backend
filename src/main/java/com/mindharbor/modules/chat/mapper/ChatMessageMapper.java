package com.mindharbor.modules.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mindharbor.modules.chat.entity.ChatMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Delete;
import java.util.List;
import java.util.Optional;

@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {

    @Select("SELECT * FROM chat_messages WHERE id = #{id} LIMIT 1")
    Optional<ChatMessage> findById(Long id);

    @Select("SELECT * FROM chat_messages WHERE session_id = #{sessionId} ORDER BY created_at ASC")
    List<ChatMessage> findBySessionIdOrderByCreatedAtAsc(Long sessionId);

    @Delete("DELETE FROM chat_messages WHERE session_id = #{sessionId}")
    int deleteBySessionId(Long sessionId);

    @Select("SELECT m.* FROM chat_messages m JOIN chat_sessions s ON s.id = m.session_id WHERE s.user_id = #{userId} AND m.favorite = 1 ORDER BY m.created_at DESC")
    List<ChatMessage> findBySessionUserIdAndFavoriteTrueOrderByCreatedAtDesc(Long userId);
}
