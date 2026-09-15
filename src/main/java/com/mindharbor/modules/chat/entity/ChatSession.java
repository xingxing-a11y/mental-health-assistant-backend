package com.mindharbor.modules.chat.entity;

import com.mindharbor.modules.user.entity.User;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@TableName("chat_sessions")
public class ChatSession {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    @TableField(exist = false)
    private User user;

    private String name;

    private String preview;

    private Instant latestMessageAt = Instant.now();

    private boolean contextCleared;

    private boolean highRisk;

    private Mode mode = Mode.COMPANION;

    public enum Mode {
        COMPANION,
        ANALYTICAL
    }
}


