package com.mindharbor.modules.risk.vo;

import java.time.Instant;

/**
 * 管理端看到的高危预警。
 *
 * <p>刻意不暴露 {@code refs}：那是内部的关联编号（messageId/sessionId/resultId），
 * 管理员排查问题时用不上，暴露出去反而增加越权翻查的可能性。
 */
public record RiskAlertVO(
        Long id,
        String alertId,
        Long userId,
        String source,
        String level,
        String summary,
        String publishStatus,
        int publishAttempts,
        Instant occurredAt,
        Instant archivedAt,
        Instant createdAt) {
}
