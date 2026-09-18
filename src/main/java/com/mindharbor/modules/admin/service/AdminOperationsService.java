package com.mindharbor.modules.admin.service;

import com.mindharbor.modules.admin.vo.HighRiskAuditVO;
import com.mindharbor.modules.admin.vo.StatisticsOverviewVO;
import java.util.List;

/** 管理后台运营数据业务接口：高危审计与统计概览。 */
public interface AdminOperationsService {

    List<HighRiskAuditVO> highRiskAudits();

    StatisticsOverviewVO statisticsOverview();
}
