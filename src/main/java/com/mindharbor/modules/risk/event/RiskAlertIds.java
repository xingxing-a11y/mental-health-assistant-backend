package com.mindharbor.modules.risk.event;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/** alertId 生成规则：同一语义事件只能产生同一个键，这是全链路幂等的起点。 */
public final class RiskAlertIds {

    /** 输出 8 位紧凑日期（20261123），不用带连字符的 ISO 格式，避免键里出现分隔歧义。 */
    private static final DateTimeFormatter COMPACT_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private RiskAlertIds() {
    }

    /** 同一条用户消息只告警一次；用户重发同一句话算新消息，是新事件。 */
    public static String chat(long messageId) {
        return "chat:" + messageId;
    }

    /** 同一份测评结果只告警一次。 */
    public static String assessment(long resultId) {
        return "assessment:" + resultId;
    }

    /** 同一用户同一天只告警一次，定时任务重跑不会重复。 */
    public static String diary(long userId, LocalDate day) {
        return "diary:" + userId + ":" + day.format(COMPACT_DATE);
    }
}
