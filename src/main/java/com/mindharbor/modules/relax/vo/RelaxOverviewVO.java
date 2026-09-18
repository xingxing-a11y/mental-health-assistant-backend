package com.mindharbor.modules.relax.vo;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class RelaxOverviewVO {

    private long weekPracticeCount;

    private Double weekAverageStress;

    private long totalPracticeCount;

    private Integer latestStressLevel;

    private Instant latestStressAt;
}
