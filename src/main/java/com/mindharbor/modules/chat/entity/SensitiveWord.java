package com.mindharbor.modules.chat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 敏感词词条，级别为 CARE（关注）或 HIGH（高危）。 */
@Getter
@Setter
@NoArgsConstructor
@TableName("sensitive_words")
public class SensitiveWord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String word;

    private Level level = Level.CARE;

    private boolean enabled = true;

    private Instant createdAt = Instant.now();

    private Instant updatedAt = Instant.now();

    public enum Level {
        CARE,
        HIGH
    }
}
