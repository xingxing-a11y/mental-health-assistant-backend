package com.mindharbor.modules.chat.vo;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 敏感词词条视图，只对管理员开放。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SensitiveWordVO {

    private Long id;

    private String word;

    private String level;

    private boolean enabled;

    private Instant createdAt;

    private Instant updatedAt;
}
