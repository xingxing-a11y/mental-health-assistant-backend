package com.mindharbor.modules.admin.service;

import com.mindharbor.modules.admin.entity.AdminOperationLog;
import com.mindharbor.modules.admin.vo.AdminOperationLogVO;
import com.mindharbor.modules.user.vo.PageResult;
import java.time.LocalDate;

/** 管理员操作审计业务接口：显式记录，不使用隐式切面。 */
public interface AdminOperationLogService {

    /** 记录一次管理员写操作，摘要只描述动作与目标，不写业务正文。 */
    void record(AdminOperationLog.Type type, String targetType, Long targetId, String summary);

    PageResult<AdminOperationLogVO> list(
            int page,
            int size,
            String type,
            LocalDate startDate,
            LocalDate endDate);
}
