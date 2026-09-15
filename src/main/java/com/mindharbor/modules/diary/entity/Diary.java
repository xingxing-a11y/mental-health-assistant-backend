package com.mindharbor.modules.diary.entity;

import com.mindharbor.modules.user.entity.User;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@TableName("diaries")
public class Diary {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    @TableField(exist = false)
    private User user;

    private String content;

    private Emotion emotion;

    private Instant createdAt = Instant.now();

    public enum Emotion {
        HAPPY, CALM, STRESSED, ANXIOUS, LOW
    }
}
