package com.mindharbor.modules.risk.service;

import com.mindharbor.common.PageResult;
import com.mindharbor.modules.risk.vo.RiskAlertDailyStatVO;
import com.mindharbor.modules.risk.vo.RiskAlertVO;
import java.util.List;

/** 管理端高危预警查询与人工处理。 */
public interface AdminRiskAlertService {

    PageResult<RiskAlertVO> list(int page, int size);

    /** 最近若干天的按日统计，天数会被夹到合理区间。 */
    List<RiskAlertDailyStatVO> stats(int days);

    /** 人工归档（标记已处理）；重复归档视为幂等，但不存在时报 404。 */
    void archive(Long id);
}
