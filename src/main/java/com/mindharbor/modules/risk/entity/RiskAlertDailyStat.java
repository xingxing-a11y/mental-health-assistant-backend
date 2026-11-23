package com.mindharbor.modules.risk.entity;

import java.time.LocalDate;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 按日预聚合结果，仅供管理端统计接口读取，不参与写路径。 */
@Getter
@Setter
@NoArgsConstructor
public class RiskAlertDailyStat {

    private LocalDate statDate;

    private String source;

    private Integer total;

    private Instant updatedAt;
}
