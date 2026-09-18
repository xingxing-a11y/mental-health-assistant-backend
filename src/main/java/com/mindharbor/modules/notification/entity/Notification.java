package com.mindharbor.modules.notification.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 系统通知主表。
 * 全体推送只落一行，读取时按用户动态判定可见性，不为每个用户写通知记录。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("notifications")
public class Notification {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Scope scope = Scope.ALL;

    private Long targetUserId;

    private String title;

    private String content;

    private Long createdBy;

    private Instant createdAt = Instant.now();

    /** null 表示永不过期。 */
    private Instant expireAt;

    /** 读时扇出的已读时间，来自关联表，不落库到本表。 */
    @TableField(exist = false)
    private Instant readAt;

    public enum Scope {
        ALL,
        USER
    }
}
