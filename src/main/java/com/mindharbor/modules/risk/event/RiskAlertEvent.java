package com.mindharbor.modules.risk.event;

import java.time.Instant;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 风险预警事件消息体。
 *
 * <p>只带描述与关联编号，<strong>绝不携带用户原文</strong> —— 审计与消息都不落原文，
 * 与 existing「sensitive_events 只记词条编号与级别」的规矩保持一致。
 */
@Getter
@Setter
@NoArgsConstructor
public class RiskAlertEvent {

    /** 幂等键：chat:{messageId} / assessment:{resultId} / diary:{userId}:{yyyyMMdd}。 */
    private String alertId;

    private Long userId;

    private Source source;

    private Level level;

    /** 只描述事件，不含用户原文。 */
    private String summary;

    /** 关联编号：sessionId / messageId / resultId 等。 */
    private Map<String, String> refs;

    private Instant occurredAt;

    /** 触发来源，决定回访是否生效。 */
    public enum Source {
        CHAT_HIGH,
        ASSESSMENT_HIGH,
        DIARY_STREAK
    }

    public enum Level {
        HIGH,
        MEDIUM
    }
}
