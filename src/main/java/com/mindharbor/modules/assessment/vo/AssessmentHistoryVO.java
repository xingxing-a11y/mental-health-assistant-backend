package com.mindharbor.modules.assessment.vo;

import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 测评历史视图，只返回当前用户自己的记录，不暴露 userId、scaleId 等内部标识。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AssessmentHistoryVO {

    private Long id;

    private String scaleName;

    private Integer totalScore;

    private Double standardScore;

    private String rangeDescription;

    private List<String> suggestions;

    private Instant createdAt;
}
