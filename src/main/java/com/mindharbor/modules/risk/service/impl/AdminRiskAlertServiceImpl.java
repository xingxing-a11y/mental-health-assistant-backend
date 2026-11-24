package com.mindharbor.modules.risk.service.impl;

import com.mindharbor.common.PageResult;
import com.mindharbor.common.ResourceNotFoundException;
import com.mindharbor.modules.risk.dto.RiskAlertHandleRequest;
import com.mindharbor.modules.risk.entity.RiskAlert;
import com.mindharbor.modules.risk.mapper.RiskAlertDailyStatMapper;
import com.mindharbor.modules.risk.mapper.RiskAlertMapper;
import com.mindharbor.modules.risk.service.AdminRiskAlertService;
import com.mindharbor.modules.risk.vo.RiskAlertDailyStatVO;
import com.mindharbor.modules.risk.vo.RiskAlertVO;
import com.mindharbor.modules.user.entity.User;
import com.mindharbor.modules.user.mapper.UserMapper;
import com.mindharbor.security.CurrentUserProvider;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 管理端预警查询实现。 */
@Service
public class AdminRiskAlertServiceImpl implements AdminRiskAlertService {

    private static final int DEFAULT_PAGE_SIZE = 20;

    private static final int MAX_PAGE_SIZE = 50;

    private static final int MAX_STAT_DAYS = 90;

    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Shanghai");

    private final RiskAlertMapper riskAlertMapper;

    private final RiskAlertDailyStatMapper dailyStatMapper;

    private final CurrentUserProvider currentUserProvider;

    private final UserMapper userMapper;

    public AdminRiskAlertServiceImpl(
            RiskAlertMapper riskAlertMapper,
            RiskAlertDailyStatMapper dailyStatMapper,
            CurrentUserProvider currentUserProvider,
            UserMapper userMapper) {
        this.riskAlertMapper = riskAlertMapper;
        this.dailyStatMapper = dailyStatMapper;
        this.currentUserProvider = currentUserProvider;
        this.userMapper = userMapper;
    }

    @Override
    public PageResult<RiskAlertVO> list(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        // offset 必须用 long 参与乘法：超大页码乘出来会溢出成负数，分页直接报错。
        long offset = (long) safePage * safeSize;

        List<RiskAlert> rows = riskAlertMapper.findPage(safeSize, offset);
        Map<Long, String> accounts = loadAccounts(rows);
        List<RiskAlertVO> items = rows.stream()
                .map(row -> toVo(row, accounts.get(row.getUserId())))
                .toList();
        long total = riskAlertMapper.countAll();
        return new PageResult<>(items, safePage, safeSize, total, totalPages(total, safeSize));
    }

    /** 一次批量取完本页涉及的账号，避免逐行查库；用户已注销时查不到，username 为 null。 */
    private Map<Long, String> loadAccounts(List<RiskAlert> rows) {
        List<Long> userIds = rows.stream()
                .map(RiskAlert::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return userMapper.findAccountsByIds(userIds).stream()
                .filter(user -> user.getId() != null && user.getUsername() != null)
                .collect(Collectors.toMap(User::getId, User::getUsername));
    }

    @Override
    public List<RiskAlertDailyStatVO> stats(int days) {
        int safeDays = Math.min(Math.max(days, 1), MAX_STAT_DAYS);
        LocalDate from = LocalDate.now(APP_ZONE).minusDays(safeDays - 1L);
        return dailyStatMapper.findSince(from).stream()
                .map(row -> new RiskAlertDailyStatVO(
                        row.getStatDate(), row.getSource(), row.getTotal() == null ? 0 : row.getTotal()))
                .toList();
    }

    @Override
    @Transactional
    public void handle(Long id, RiskAlertHandleRequest request) {
        // 先查一次区分「不存在」与「已处理」：不存在报 404，已处理则允许改结论。
        RiskAlert alert = riskAlertMapper.selectById(id);
        if (alert == null) {
            throw new ResourceNotFoundException("预警不存在");
        }
        riskAlertMapper.markHandled(
                id,
                request.getHandleResult(),
                normalizeNote(request.getHandleNote()),
                currentUserProvider.getUserId(),
                Instant.now());
    }

    /** 空白备注存 null 而不是空串，便于前端区分「没写」与「写了空」。 */
    private static String normalizeNote(String note) {
        if (note == null) {
            return null;
        }
        String trimmed = note.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static RiskAlertVO toVo(RiskAlert alert, String username) {
        return new RiskAlertVO(
                alert.getId(),
                alert.getAlertId(),
                alert.getUserId(),
                username,
                alert.getSource() == null ? null : alert.getSource().name(),
                alert.getLevel() == null ? null : alert.getLevel().name(),
                alert.getSummary(),
                alert.getPublishStatus() == null ? null : alert.getPublishStatus().name(),
                alert.getPublishAttempts() == null ? 0 : alert.getPublishAttempts(),
                alert.getOccurredAt(),
                alert.getArchivedAt(),
                alert.getHandleResult() == null ? null : alert.getHandleResult().name(),
                alert.getHandleNote(),
                alert.getHandledBy(),
                alert.getCreatedAt());
    }

    private static int totalPages(long total, int size) {
        return (int) ((total + size - 1) / size);
    }
}
