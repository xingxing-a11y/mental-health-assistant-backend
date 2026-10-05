package com.mindharbor.modules.feedback.service.impl;

import com.mindharbor.common.PageResult;
import com.mindharbor.common.ResourceNotFoundException;
import com.mindharbor.modules.admin.entity.AdminOperationLog;
import com.mindharbor.modules.admin.service.AdminOperationLogService;
import com.mindharbor.modules.feedback.dto.FeedbackHandleRequest;
import com.mindharbor.modules.feedback.dto.FeedbackRequest;
import com.mindharbor.modules.feedback.entity.Feedback;
import com.mindharbor.modules.feedback.mapper.FeedbackMapper;
import com.mindharbor.modules.feedback.service.FeedbackService;
import com.mindharbor.modules.feedback.vo.FeedbackVO;
import com.mindharbor.security.CurrentUserProvider;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;

/** 反馈工单业务实现。 */
@Service
public class FeedbackServiceImpl implements FeedbackService {

    private static final int DEFAULT_PAGE_SIZE = 20;

    private static final int MAX_PAGE_SIZE = 50;

    private final FeedbackMapper feedbackMapper;

    private final CurrentUserProvider currentUserProvider;

    private final AdminOperationLogService adminOperationLogService;

    public FeedbackServiceImpl(
            FeedbackMapper feedbackMapper,
            CurrentUserProvider currentUserProvider,
            AdminOperationLogService adminOperationLogService) {
        this.feedbackMapper = feedbackMapper;
        this.currentUserProvider = currentUserProvider;
        this.adminOperationLogService = adminOperationLogService;
    }

    @Override
    public FeedbackVO submit(FeedbackRequest request) {
        Feedback feedback = new Feedback();
        feedback.setUserId(currentUserProvider.getUserId());
        feedback.setType(request.getType());
        feedback.setContent(request.getContent().trim());
        feedback.setContact(normalizeContact(request.getContact()));
        feedback.setStatus(Feedback.Status.PENDING);
        feedbackMapper.insert(feedback);
        return toView(feedback);
    }

    @Override
    public PageResult<FeedbackVO> list(int page, int size, String status) {
        String normalizedStatus = normalizeStatus(status);
        int safePage = Math.max(page, 0);
        int safeSize = normalizeSize(size);
        long total = feedbackMapper.countPage(normalizedStatus);
        List<FeedbackVO> items = feedbackMapper
                .findPage(normalizedStatus, safeSize, (long) safePage * safeSize)
                .stream()
                .map(this::toView)
                .toList();
        return new PageResult<>(items, safePage, safeSize, total, totalPages(total, safeSize));
    }

    @Override
    public FeedbackVO handle(long feedbackId, FeedbackHandleRequest request) {
        Feedback feedback = feedbackMapper.selectById(feedbackId);
        if (feedback == null) {
            throw new ResourceNotFoundException("反馈不存在");
        }
        feedback.setStatus(request.getStatus());
        feedback.setRemark(normalizeRemark(request.getRemark()));
        feedback.setUpdatedAt(Instant.now());
        feedbackMapper.updateById(feedback);
        adminOperationLogService.record(
                AdminOperationLog.Type.FEEDBACK,
                "feedback",
                feedback.getId(),
                "处理反馈工单，状态变更为 " + feedback.getStatus().name());
        return toView(feedback);
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return Feedback.Status.valueOf(status.trim().toUpperCase()).name();
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("反馈状态无效");
        }
    }

    private String normalizeContact(String contact) {
        if (contact == null) {
            return null;
        }
        String trimmed = contact.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizeRemark(String remark) {
        if (remark == null) {
            return null;
        }
        String trimmed = remark.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private FeedbackVO toView(Feedback feedback) {
        return new FeedbackVO(
                feedback.getId(),
                feedback.getUserId(),
                feedback.getType() == null ? null : feedback.getType().name(),
                feedback.getContent(),
                feedback.getContact(),
                feedback.getStatus() == null ? null : feedback.getStatus().name(),
                feedback.getRemark(),
                feedback.getCreatedAt(),
                feedback.getUpdatedAt());
    }

    private int normalizeSize(int size) {
        if (size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    private int totalPages(long total, int size) {
        return (int) ((total + size - 1) / size);
    }
}
