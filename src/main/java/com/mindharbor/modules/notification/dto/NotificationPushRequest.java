package com.mindharbor.modules.notification.dto;

import com.mindharbor.modules.notification.entity.Notification;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 管理员推送心理小贴士请求。 */
@Getter
@Setter
@NoArgsConstructor
public class NotificationPushRequest {

    @NotNull(message = "推送范围不能为空")
    private Notification.Scope scope;

    /** scope 为 USER 时必填，为 ALL 时忽略。 */
    private Long userId;

    @NotBlank(message = "标题不能为空")
    @Size(max = 200, message = "标题长度不能超过 200 字")
    private String title;

    @NotBlank(message = "内容不能为空")
    @Size(max = 2000, message = "内容长度不能超过 2000 字")
    private String content;

    /** 可选，未提供时默认为 30 天后过期。 */
    private Instant expireAt;

    /** 为 true 时表示永不过期，此时忽略 expireAt。 */
    private Boolean permanent;
}
