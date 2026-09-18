package com.mindharbor.modules.assessment.vo;

import com.mindharbor.modules.assessment.entity.AssessmentQuestion;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 量表详情视图，包含题目和选项，避免 service 层返回裸 Map 结构。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AssessmentScaleDetailVO {

    private Long id;

    private String name;

    private String description;

    private String applicableScene;

    private List<AssessmentQuestion> questions;
}
