package com.mindharbor.modules.risk.mapper;

import com.mindharbor.modules.risk.entity.RiskAlertDailyStat;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 按日统计访问层。
 *
 * <p>自增必须走 {@code ON DUPLICATE KEY UPDATE total = total + 1}：先 SELECT 再 UPDATE 在并发下会丢计数，
 * 而统计少算一次就再也补不回来。
 */
@Mapper
public interface RiskAlertDailyStatMapper {

    @Insert("INSERT INTO risk_alert_daily_stats (stat_date, source, total, updated_at) "
            + "VALUES (#{statDate}, #{source}, 1, #{updatedAt}) "
            + "ON DUPLICATE KEY UPDATE total = total + 1, updated_at = #{updatedAt}")
    int increment(
            @Param("statDate") LocalDate statDate,
            @Param("source") String source,
            @Param("updatedAt") Instant updatedAt);

    @Select("SELECT stat_date, source, total, updated_at FROM risk_alert_daily_stats "
            + "WHERE stat_date >= #{from} ORDER BY stat_date DESC, source ASC")
    List<RiskAlertDailyStat> findSince(@Param("from") LocalDate from);
}
