package com.mindharbor.modules.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mindharbor.modules.chat.entity.ChatSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import java.util.List;
import java.util.Optional;

@Mapper
public interface ChatSessionMapper extends BaseMapper<ChatSession> {

    @Select("SELECT * FROM chat_sessions WHERE id = #{id} LIMIT 1")
    Optional<ChatSession> findById(Long id);

    @Select("SELECT * FROM chat_sessions WHERE user_id = #{userId} AND archived = FALSE ORDER BY pinned DESC, latest_message_at DESC")
    List<ChatSession> findByUserIdOrderByLatestMessageAtDesc(Long userId);

    @Select("SELECT * FROM chat_sessions WHERE user_id = #{userId} AND archived = TRUE ORDER BY latest_message_at DESC")
    List<ChatSession> findArchivedByUserIdOrderByLatestMessageAtDesc(Long userId);

}
