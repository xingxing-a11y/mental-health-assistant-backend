package com.mindharbor.modules.risk.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 24 小时关怀回访任务。
 *
 * <p>这张表是<strong>真源</strong>，延迟队列只是"闹钟"：消息丢了由补偿任务按 {@code status + due_at}
 * 扫描补发，所以队列不是必达路径。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("care_followups")
public class CareFollowup {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String alertId;

    private Long userId;

    private Instant dueAt;

    private Status status = Status.PENDING;

    private Instant sentAt;

    private Instant createdAt = Instant.now();

    public enum Status {
        PENDING,
        SENT,
        SKIPPED
    }
}
