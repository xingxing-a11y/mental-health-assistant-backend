package com.mindharbor.modules.notification.vo;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 通知视图，read 表示当前用户是否已读。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class NotificationVO {

    private Long id;

    private String scope;

    private String title;

    private String content;

    private Instant createdAt;

    private Instant expireAt;

    private boolean read;
}
