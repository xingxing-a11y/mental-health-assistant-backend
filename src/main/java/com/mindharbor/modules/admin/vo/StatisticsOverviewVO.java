package com.mindharbor.modules.admin.vo;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 管理后台统计概览视图。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StatisticsOverviewVO {

    private long registeredUsers;

    private long assessmentCompletions;

    private long chatSessions;
}
