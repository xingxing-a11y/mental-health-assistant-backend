package com.mindharbor.modules.risk.vo;

import java.time.Instant;

/**
 * 管理端看到的高危预警。
 *
 * <p>刻意不暴露 {@code refs}：那是内部的关联编号（messageId/sessionId/resultId），
 * 管理员排查问题时用不上，暴露出去反而增加越权翻查的可能性。
 *
 * <p>{@code username} 是出事账号（users.username）。管理员要据此判断是谁、并直接给他发站内信，
 * 所以必须带上；这是账号而非昵称——昵称可重名，选人时不能用。
 */
public record RiskAlertVO(
        Long id,
        String alertId,
        Long userId,
        String username,
        String source,
        String level,
        String summary,
        String publishStatus,
        int publishAttempts,
        Instant occurredAt,
        Instant archivedAt,
        String handleResult,
        String handleNote,
        Long handledBy,
        Instant createdAt) {
}
