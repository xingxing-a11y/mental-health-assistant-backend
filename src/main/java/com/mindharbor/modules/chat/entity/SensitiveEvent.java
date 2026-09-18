package com.mindharbor.modules.chat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 敏感词命中审计，只记录词条编号与级别，不保存用户原文。 */
@Getter
@Setter
@NoArgsConstructor
@TableName("sensitive_events")
public class SensitiveEvent {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long sessionId;

    private Long wordId;

    private SensitiveWord.Level level;

    private Instant createdAt = Instant.now();
}
