package com.mindharbor.modules.relax.service;

import com.mindharbor.common.PageResult;
import com.mindharbor.modules.relax.dto.RelaxRecordRequest;
import com.mindharbor.modules.relax.dto.StressLogRequest;
import com.mindharbor.modules.relax.vo.RelaxMeditationVO;
import com.mindharbor.modules.relax.vo.RelaxOverviewVO;
import com.mindharbor.modules.relax.vo.RelaxRecordVO;
import com.mindharbor.modules.relax.vo.StressLogVO;
import com.mindharbor.modules.relax.vo.StressTrendPointVO;
import java.util.List;

/** 放松小工具业务接口：呼吸放松记录、冥想脚本、压力打卡与趋势。 */
public interface RelaxService {

    RelaxOverviewVO overview();

    List<RelaxMeditationVO> meditations();

    PageResult<RelaxRecordVO> records(int page, int size, String type);

    RelaxRecordVO createRecord(RelaxRecordRequest request);

    PageResult<StressLogVO> stressLogs(int page, int size);

    StressLogVO createStressLog(StressLogRequest request);

    List<StressTrendPointVO> stressTrend(int days);
}
