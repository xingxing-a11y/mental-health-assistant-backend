package com.mindharbor.modules.risk.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.mindharbor.modules.risk.event.RiskAlertEvent;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 高危信号事件主表。
 *
 * <p>它同时承担发布 outbox 的职责：写库与发消息分两步，发消息失败时置 {@code publish_status=FAILED}，
 * 由补偿任务重投。因此这张表既是"风险档案"，也是"投递状态机"。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("risk_alerts")
public class RiskAlert {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 幂等键：chat:{messageId} / assessment:{resultId} / diary:{userId}:{yyyyMMdd}。 */
    private String alertId;

    private Long userId;

    private RiskAlertEvent.Source source;

    private RiskAlertEvent.Level level;

    /** 只描述事件，不含用户原文。 */
    private String summary;

    /** JSON 字符串：sessionId / messageId / resultId 等关联编号。 */
    private String refs;

    private Instant occurredAt;

    private PublishStatus publishStatus = PublishStatus.PENDING;

    private Integer publishAttempts = 0;

    /** 管理员人工标记「已处理」的时间；null 表示未处理。 */
    private Instant archivedAt;

    /** 处理结论；历史数据可能为空（那时只记了处理时间）。 */
    private HandleResult handleResult;

    private String handleNote;

    /** 处理人（管理员用户编号）。 */
    private Long handledBy;

    private Instant createdAt = Instant.now();

    public enum PublishStatus {
        PENDING,
        SENT,
        FAILED
    }
}
