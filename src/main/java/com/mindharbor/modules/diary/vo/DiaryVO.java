package com.mindharbor.modules.diary.vo;

import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 情绪日记视图对象。
 * 不直接序列化实体：实体的 {@code user} 关联字段永远是 null，却会出现在响应体里。
 * emotion 用字符串而非枚举，与 {@code UserVO} 的 role/status 保持一致的写法。
 */
@Getter
@Setter
@NoArgsConstructor
public class DiaryVO {

    private Long id;

    private Long userId;

    private String content;

    private String emotion;

    private Instant createdAt;
}
