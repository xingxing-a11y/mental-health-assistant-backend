package com.mindharbor.modules.admin.service.impl;

import com.mindharbor.common.PageResult;
import com.mindharbor.modules.admin.entity.AdminOperationLog;
import com.mindharbor.modules.admin.mapper.AdminOperationLogMapper;
import com.mindharbor.modules.admin.service.AdminOperationLogService;
import com.mindharbor.modules.admin.vo.AdminOperationLogVO;
import com.mindharbor.modules.user.entity.User;
import com.mindharbor.modules.user.mapper.UserMapper;
import com.mindharbor.security.CurrentUserProvider;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** 管理员操作审计实现，写入失败只记日志，不影响业务操作本身。 */
@Service
public class AdminOperationLogServiceImpl implements AdminOperationLogService {

    private static final Logger log = LoggerFactory.getLogger(AdminOperationLogServiceImpl.class);

    private static final int DEFAULT_PAGE_SIZE = 20;

    private static final int MAX_PAGE_SIZE = 50;

    private static final int MAX_SUMMARY_LENGTH = 500;

    private final AdminOperationLogMapper adminOperationLogMapper;

    private final UserMapper userMapper;

    private final CurrentUserProvider currentUserProvider;

    public AdminOperationLogServiceImpl(
            AdminOperationLogMapper adminOperationLogMapper,
            UserMapper userMapper,
            CurrentUserProvider currentUserProvider) {
        this.adminOperationLogMapper = adminOperationLogMapper;
        this.userMapper = userMapper;
        this.currentUserProvider = currentUserProvider;
    }

    @Override
    public void record(AdminOperationLog.Type type, String targetType, Long targetId, String summary) {
        try {
            Long operatorId = currentUserProvider.getUserId();
            //new时自动将当前时间进行赋值
            AdminOperationLog entry = new AdminOperationLog();
            entry.setOperatorId(operatorId);
            entry.setOperatorName(resolveOperatorName(operatorId));
            entry.setType(type);
            entry.setTargetType(targetType);
            entry.setTargetId(targetId);
            entry.setSummary(truncate(summary));
            adminOperationLogMapper.insert(entry);
        } catch (RuntimeException exception) {
            log.error("Failed to record admin operation log, type={}", type, exception);
        }
    }

    @Override
    public PageResult<AdminOperationLogVO> list(
            int page,
            int size,
            String type,
            LocalDate startDate,
            LocalDate endDate) {
        String normalizedType = normalizeType(type);
        Instant startAt = startDate == null ? null : startOfDay(startDate);
        //23:59:99999...
        Instant endAt = endDate == null ? null : endOfDay(endDate);
        int safePage = Math.max(page, 0);
        int safeSize = normalizeSize(size);
        long total = adminOperationLogMapper.countPage(normalizedType, startAt, endAt);
        List<AdminOperationLogVO> items = adminOperationLogMapper
                .findPage(normalizedType, startAt, endAt, safeSize, safePage * safeSize)
                .stream()
                .map(entry -> new AdminOperationLogVO(
                        entry.getId(),
                        entry.getOperatorId(),
                        entry.getOperatorName(),
                        entry.getType() == null ? null : entry.getType().name(),
                        entry.getTargetType(),
                        entry.getTargetId(),
                        entry.getSummary(),
                        entry.getCreatedAt()))
                .toList();
        return new PageResult<>(items, safePage, safeSize, total, totalPages(total, safeSize));
    }

    private String resolveOperatorName(Long operatorId) {
        return userMapper.findById(operatorId)
                .map(User::getUsername)
                .orElse(null);
    }

    private String normalizeType(String type) {
        if (type == null || type.isBlank()) {
            return null;
        }
        try {
            return AdminOperationLog.Type.valueOf(type.trim().toUpperCase()).name();
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("操作类型无效");
        }
    }

    private Instant startOfDay(LocalDate date) {
        return date.atStartOfDay(ZoneId.systemDefault()).toInstant();
    }

    private Instant endOfDay(LocalDate date) {
        return date.atTime(LocalTime.MAX).atZone(ZoneId.systemDefault()).toInstant();
    }

    private String truncate(String summary) {
        if (summary == null) {
            return null;
        }
        return summary.length() <= MAX_SUMMARY_LENGTH
                ? summary
                : summary.substring(0, MAX_SUMMARY_LENGTH);
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
