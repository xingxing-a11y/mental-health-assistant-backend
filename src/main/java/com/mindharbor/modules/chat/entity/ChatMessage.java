package com.mindharbor.modules.chat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@TableName("chat_messages")
public class ChatMessage {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long sessionId;

    /** 关联会话仅用于业务判断，序列化时不输出。 */
    @JsonIgnore
    @TableField(exist = false)
    private ChatSession session;

    private Role role;

    private String content;

    private Instant createdAt = Instant.now();

    private boolean favorite;

    public enum Role {
        USER,
        ASSISTANT
    }
}
