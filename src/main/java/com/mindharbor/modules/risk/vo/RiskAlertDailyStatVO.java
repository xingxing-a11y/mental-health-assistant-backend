package com.mindharbor.modules.risk.vo;

import java.time.LocalDate;

/** 按日统计的一行，供管理端趋势图使用。 */
public record RiskAlertDailyStatVO(LocalDate statDate, String source, int total) {
}
