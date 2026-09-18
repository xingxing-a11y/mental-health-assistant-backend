package com.mindharbor.modules.relax.vo;

import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 压力趋势点：由日期聚合查询直接映射，date 为 log_date，averageLevel 为 average_level。 */
@Getter
@Setter
@NoArgsConstructor
public class StressTrendPointVO {

    private LocalDate logDate;

    private Double averageLevel;
}
