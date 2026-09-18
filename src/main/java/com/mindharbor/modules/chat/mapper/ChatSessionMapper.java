package com.mindharbor.modules.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mindharbor.modules.chat.entity.ChatSession;
import java.util.List;
import java.util.Optional;
import java.time.Instant;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 会话数据访问层，只声明查询语句，不承载业务判断。 */
@Mapper
public interface ChatSessionMapper extends BaseMapper<ChatSession> {

    @Select("SELECT * FROM chat_sessions WHERE id = #{id} LIMIT 1")
    Optional<ChatSession> findById(Long id);

    @Select("SELECT * FROM chat_sessions WHERE user_id = #{userId} AND archived = FALSE "
            + "ORDER BY pinned DESC, latest_message_at DESC")
    List<ChatSession> findByUserIdOrderByLatestMessageAtDesc(Long userId);

    @Select("SELECT * FROM chat_sessions WHERE user_id = #{userId} AND archived = TRUE "
            + "ORDER BY latest_message_at DESC")
    List<ChatSession> findArchivedByUserIdOrderByLatestMessageAtDesc(Long userId);

    @Select("SELECT * FROM chat_sessions WHERE high_risk = TRUE ORDER BY latest_message_at DESC")
    List<ChatSession> findByHighRiskTrueOrderByLatestMessageAtDesc();

    /** 分批清理已无消息且已过保留期的会话，单次删除带 LIMIT。 */
    @Delete("DELETE FROM chat_sessions WHERE user_id = #{userId} "
            + "AND latest_message_at <= #{before} "
            + "AND NOT EXISTS (SELECT 1 FROM chat_messages m WHERE m.session_id = chat_sessions.id) "
            + "LIMIT #{limit}")
    int deleteEmptyExpiredBatch(
            @Param("userId") Long userId,
            @Param("before") Instant before,
            @Param("limit") int limit);
}
