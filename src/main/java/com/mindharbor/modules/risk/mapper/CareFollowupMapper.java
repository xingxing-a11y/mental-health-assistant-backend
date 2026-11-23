package com.mindharbor.modules.risk.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mindharbor.modules.risk.entity.CareFollowup;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 24h 回访任务访问层。表是真源，延迟队列只负责定时唤醒。 */
@Mapper
public interface CareFollowupMapper extends BaseMapper<CareFollowup> {

    /** 同一 alertId 只留一条任务；重复发布不产生第二条回访。 */
    @Insert("INSERT IGNORE INTO care_followups "
            + "(alert_id, user_id, due_at, status, created_at) "
            + "VALUES (#{alertId}, #{userId}, #{dueAt}, #{status}, #{createdAt})")
    int insertIgnore(CareFollowup followup);

    @Select("SELECT * FROM care_followups WHERE alert_id = #{alertId} LIMIT 1")
    Optional<CareFollowup> findByAlertId(@Param("alertId") String alertId);

    /** 只有仍处于 PENDING 的任务才标记为已发送，避免重复发关怀消息。 */
    @Update("UPDATE care_followups SET status = 'SENT', sent_at = #{sentAt} "
            + "WHERE id = #{id} AND status = 'PENDING'")
    int markSent(@Param("id") Long id, @Param("sentAt") Instant sentAt);

    /** 兜底扫描：到期但仍是 PENDING 的任务（延迟消息丢失时靠它补发）。 */
    @Select("SELECT * FROM care_followups "
            + "WHERE status = 'PENDING' AND due_at <= #{now} "
            + "ORDER BY id ASC LIMIT #{limit}")
    List<CareFollowup> findDue(@Param("now") Instant now, @Param("limit") int limit);
}
