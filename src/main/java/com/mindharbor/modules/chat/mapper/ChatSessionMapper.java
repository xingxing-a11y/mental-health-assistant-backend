package com.mindharbor.modules.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mindharbor.modules.chat.entity.ChatSession;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 会话数据访问层，语句写在 resources/mapper/chat/ChatSessionMapper.xml。 */
@Mapper
public interface ChatSessionMapper extends BaseMapper<ChatSession> {

    Optional<ChatSession> findById(@Param("id") Long id);

    List<ChatSession> findByUserIdOrderByLatestMessageAtDesc(@Param("userId") Long userId);

    List<ChatSession> findArchivedByUserIdOrderByLatestMessageAtDesc(@Param("userId") Long userId);

    List<ChatSession> findByHighRiskTrueOrderByLatestMessageAtDesc();

    int deleteEmptyExpiredBatch(
            @Param("userId") Long userId,
            @Param("before") Instant before,
            @Param("limit") int limit);
}