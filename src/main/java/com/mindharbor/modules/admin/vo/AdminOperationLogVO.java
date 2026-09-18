package com.mindharbor.modules.admin.vo;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 管理员操作日志视图。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AdminOperationLogVO {

    private Long id;

    private Long operatorId;

    private String operatorName;

    private String type;

    private String targetType;

    private Long targetId;

    private String summary;

    private Instant createdAt;
}
