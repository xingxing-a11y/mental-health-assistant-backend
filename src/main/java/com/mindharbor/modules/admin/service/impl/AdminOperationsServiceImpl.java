package com.mindharbor.modules.admin.service.impl;

import com.mindharbor.modules.admin.service.AdminOperationsService;
import com.mindharbor.modules.admin.vo.HighRiskAuditVO;
import com.mindharbor.modules.admin.vo.StatisticsOverviewVO;
import com.mindharbor.modules.assessment.mapper.AssessmentResultMapper;
import com.mindharbor.modules.chat.mapper.ChatSessionMapper;
import com.mindharbor.modules.user.mapper.UserMapper;
import java.util.List;
import org.springframework.stereotype.Service;

/** 管理后台运营数据业务实现。 */
@Service
public class AdminOperationsServiceImpl implements AdminOperationsService {

    /** 审计列表只返回固定摘要文案，避免暴露完整聊天内容。 */
    private static final String HIGH_RISK_SUMMARY = "该会话已触发高风险干预";

    private final ChatSessionMapper chatSessionMapper;
    private final UserMapper userMapper;
    private final AssessmentResultMapper assessmentResultMapper;

    public AdminOperationsServiceImpl(
            ChatSessionMapper chatSessionMapper,
            UserMapper userMapper,
            AssessmentResultMapper assessmentResultMapper) {
        this.chatSessionMapper = chatSessionMapper;
        this.userMapper = userMapper;
        this.assessmentResultMapper = assessmentResultMapper;
    }

    @Override
    public List<HighRiskAuditVO> highRiskAudits() {
        return chatSessionMapper.findByHighRiskTrueOrderByLatestMessageAtDesc()
                .stream()
                .map(session -> new HighRiskAuditVO(
                        session.getId(),
                        session.getUserId(),
                        session.getLatestMessageAt(),
                        HIGH_RISK_SUMMARY))
                .toList();
    }

    @Override
    public StatisticsOverviewVO statisticsOverview() {
        return new StatisticsOverviewVO(
                userMapper.selectCount(null),
                assessmentResultMapper.selectCount(null),
                chatSessionMapper.selectCount(null));
    }
}
