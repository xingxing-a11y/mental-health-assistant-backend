package com.mindharbor.modules.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 管理员操作审计记录，只保存操作者、类型、目标与摘要，不含业务正文。 */
@Getter
@Setter
@NoArgsConstructor
@TableName("admin_operation_logs")
public class AdminOperationLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long operatorId;

    private String operatorName;

    private Type type;

    private String targetType;

    private Long targetId;

    private String summary;

    private Instant createdAt = Instant.now();

    public enum Type {
        DOCUMENT,
        SCALE,
        ARTICLE,
        ANNOUNCEMENT,
        USER,
        NOTIFICATION
    }
}
