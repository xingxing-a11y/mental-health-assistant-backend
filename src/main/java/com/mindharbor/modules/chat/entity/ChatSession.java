package com.mindharbor.modules.chat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.mindharbor.modules.user.entity.User;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@TableName("chat_sessions")
public class ChatSession {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 关联用户仅用于业务判断，序列化时不输出，避免带出账号敏感字段。 */
    @JsonIgnore
    @TableField(exist = false)
    private User user;

    private String name;

    private String preview;

    private Instant latestMessageAt = Instant.now();

    private boolean contextCleared;

    private boolean highRisk;

    private Mode mode = Mode.COMPANION;

    private boolean pinned;

    private boolean archived;

    public enum Mode {
        COMPANION,
        ANALYTICAL
    }
}
