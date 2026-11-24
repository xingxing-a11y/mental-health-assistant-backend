package com.mindharbor.modules.risk.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mindharbor.modules.risk.entity.HandleResult;
import com.mindharbor.modules.risk.entity.RiskAlert;
import com.mindharbor.modules.risk.event.RiskAlertEvent;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 风险事件主表访问层，语句写在 resources/mapper/risk/RiskAlertMapper.xml。 */
@Mapper
public interface RiskAlertMapper extends BaseMapper<RiskAlert> {

    Optional<RiskAlert> findByAlertId(@Param("alertId") String alertId);

    /**
     * 待重投的事件：状态为 PENDING/FAILED、创建超过最小间隔、重投次数未超上限。
     *
     * <p>最小间隔用来避开"刚写完库、confirm 回调还没到"的窗口，减少无谓重复发送。
     */
    List<RiskAlert> findPublishable(
            @Param("before") Instant before,
            @Param("maxAttempts") int maxAttempts,
            @Param("limit") int limit);

    int incrementPublishAttempts(@Param("alertId") String alertId);

    int markPublishStatus(
            @Param("alertId") String alertId,
            @Param("status") RiskAlert.PublishStatus status);

    /**
     * 人工处理：写入处理结论与处理人。
     *
     * <p>{@code archived_at} 用 COALESCE 保留首次处理时间——三天后补充说明时不该把"第一次处理时间"冲掉。
     */
    int markHandled(
            @Param("id") Long id,
            @Param("handleResult") HandleResult handleResult,
            @Param("handleNote") String handleNote,
            @Param("handledBy") Long handledBy,
            @Param("handledAt") Instant handledAt);

    /** 冷却期判断：该用户在某时间点之后是否已有同源告警。 */
    long countRecentBySource(
            @Param("userId") Long userId,
            @Param("source") RiskAlertEvent.Source source,
            @Param("since") Instant since);

    List<RiskAlert> findPage(@Param("limit") int limit, @Param("offset") long offset);

    long countAll();
}
