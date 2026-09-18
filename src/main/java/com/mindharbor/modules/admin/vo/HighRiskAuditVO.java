package com.mindharbor.modules.admin.vo;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 高危会话审计视图，只承载脱敏摘要，不包含任何聊天正文。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class HighRiskAuditVO {

    private Long sessionId;

    private Long userId;

    private Instant createdAt;

    private String summary;
}
