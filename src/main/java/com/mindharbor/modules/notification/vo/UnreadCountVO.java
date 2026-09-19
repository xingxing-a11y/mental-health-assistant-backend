package com.mindharbor.modules.notification.vo;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 未读通知数，替代 Controller 里用 Map 拼装的响应体。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UnreadCountVO {

    private long count;
}
