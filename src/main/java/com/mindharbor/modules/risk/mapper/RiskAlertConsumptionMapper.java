package com.mindharbor.modules.risk.mapper;

import java.time.Instant;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 消费幂等表访问层，主键为（alert_id, consumer）。
 *
 * <p>用 {@code INSERT IGNORE} 而不是先查再插：查询与插入之间的竞态会让重复消息同时通过。
 * 返回 0 就代表这条消息已被同一消费者处理过。
 */
@Mapper
public interface RiskAlertConsumptionMapper {

    @Insert("INSERT IGNORE INTO risk_alert_consumptions (alert_id, consumer, created_at) "
            + "VALUES (#{alertId}, #{consumer}, #{createdAt})")
    int insertIgnore(
            @Param("alertId") String alertId,
            @Param("consumer") String consumer,
            @Param("createdAt") Instant createdAt);

    @Delete("DELETE FROM risk_alert_consumptions WHERE created_at < #{before}")
    int deleteOlderThan(@Param("before") Instant before);
}
