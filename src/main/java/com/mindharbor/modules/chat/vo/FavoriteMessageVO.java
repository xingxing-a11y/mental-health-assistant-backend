package com.mindharbor.modules.chat.vo;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 收藏消息视图，只暴露消息本身，不包含会话或用户敏感字段。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FavoriteMessageVO {

    private Long id;

    private String content;

    private Long sessionId;

    private Instant createdAt;
}
