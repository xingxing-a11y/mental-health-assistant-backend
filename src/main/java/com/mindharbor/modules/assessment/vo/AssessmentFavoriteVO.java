package com.mindharbor.modules.assessment.vo;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AssessmentFavoriteVO {

    private Long scaleId;

    private String name;

    private String description;

    private String applicableScene;

    private boolean published;

    private Instant favoritedAt;
}
