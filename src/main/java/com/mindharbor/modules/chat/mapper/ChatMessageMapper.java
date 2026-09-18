package com.mindharbor.modules.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mindharbor.modules.chat.entity.ChatMessage;
import java.util.List;
import java.util.Optional;
import java.time.Instant;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 消息数据访问层，只声明查询语句，不承载业务判断。 */
@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {

    @Select("SELECT * FROM chat_messages WHERE id = #{id} LIMIT 1")
    Optional<ChatMessage> findById(Long id);

    @Select("SELECT * FROM chat_messages WHERE session_id = #{sessionId} ORDER BY created_at ASC")
    List<ChatMessage> findBySessionIdOrderByCreatedAtAsc(Long sessionId);

    @Delete("DELETE FROM chat_messages WHERE session_id = #{sessionId}")
    int deleteBySessionId(Long sessionId);

    /** 分批清理到期消息，单次删除带 LIMIT，避免长事务锁表。 */
    @Delete("DELETE FROM chat_messages WHERE created_at <= #{before} "
            + "AND session_id IN (SELECT id FROM chat_sessions WHERE user_id = #{userId}) "
            + "LIMIT #{limit}")
    int deleteExpiredBatch(
            @Param("userId") Long userId,
            @Param("before") Instant before,
            @Param("limit") int limit);

    /** 收藏消息分页查询，关键字与会话过滤在 SQL 内完成，业务判断仍在 service 层。 */
    @Select("<script>"
            + "SELECT m.* FROM chat_messages m "
            + "JOIN chat_sessions s ON s.id = m.session_id "
            + "WHERE s.user_id = #{userId} AND m.favorite = 1 "
            + "<if test='sessionId != null'>AND m.session_id = #{sessionId} </if>"
            + "<if test='keyword != null'>AND m.content LIKE CONCAT('%', #{keyword}, '%') </if>"
            + "ORDER BY m.created_at DESC "
            + "LIMIT #{limit} OFFSET #{offset}"
            + "</script>")
    List<ChatMessage> findFavorites(
            @Param("userId") Long userId,
            @Param("sessionId") Long sessionId,
            @Param("keyword") String keyword,
            @Param("limit") int limit,
            @Param("offset") int offset);

    @Select("<script>"
            + "SELECT COUNT(*) FROM chat_messages m "
            + "JOIN chat_sessions s ON s.id = m.session_id "
            + "WHERE s.user_id = #{userId} AND m.favorite = 1 "
            + "<if test='sessionId != null'>AND m.session_id = #{sessionId} </if>"
            + "<if test='keyword != null'>AND m.content LIKE CONCAT('%', #{keyword}, '%') </if>"
            + "</script>")
    long countFavorites(
            @Param("userId") Long userId,
            @Param("sessionId") Long sessionId,
            @Param("keyword") String keyword);
}
