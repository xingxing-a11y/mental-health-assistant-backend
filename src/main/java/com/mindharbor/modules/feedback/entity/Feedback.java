package com.mindharbor.modules.feedback.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 用户意见反馈工单。 */
@Getter
@Setter
@NoArgsConstructor
@TableName("feedbacks")
public class Feedback {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Type type = Type.OTHER;

    private String content;

    private String contact;

    private Status status = Status.PENDING;

    private String remark;

    private Instant createdAt = Instant.now();

    private Instant updatedAt;

    public enum Type {
        BUG,
        SUGGESTION,
        OTHER
    }

    public enum Status {
        PENDING,
        PROCESSED,
        REJECTED
    }
}
