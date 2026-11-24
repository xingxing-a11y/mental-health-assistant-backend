package com.mindharbor.modules.risk.service;

import com.mindharbor.common.PageResult;
import com.mindharbor.modules.risk.dto.RiskAlertHandleRequest;
import com.mindharbor.modules.risk.vo.RiskAlertDailyStatVO;
import com.mindharbor.modules.risk.vo.RiskAlertVO;
import java.util.List;

/** 管理端高危预警查询与人工处理。 */
public interface AdminRiskAlertService {

    PageResult<RiskAlertVO> list(int page, int size);

    /** 最近若干天的按日统计，天数会被夹到合理区间。 */
    List<RiskAlertDailyStatVO> stats(int days);

    /**
     * 人工处理：记录处理方式、备注与处理人。
     *
     * <p>重复处理只更新结论，{@code archived_at} 保持首次处理时间——三天后补充说明不该冲掉原时间。
     */
    void handle(Long id, RiskAlertHandleRequest request);
}
