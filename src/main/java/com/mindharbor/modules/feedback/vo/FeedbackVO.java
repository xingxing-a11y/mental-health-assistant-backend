package com.mindharbor.modules.feedback.vo;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 反馈工单视图。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FeedbackVO {

    private Long id;

    private Long userId;

    private String type;

    private String content;

    private String contact;

    private String status;

    private String remark;

    private Instant createdAt;

    private Instant updatedAt;
}
