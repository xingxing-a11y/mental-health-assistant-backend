package com.mindharbor.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 定时任务开关。
 * 具体任务类各自独立调度，一个任务异常或关闭不影响另一个。
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
