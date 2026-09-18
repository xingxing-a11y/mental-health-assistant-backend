package com.mindharbor.modules.notification.service.impl;

import com.mindharbor.common.ResourceNotFoundException;
import com.mindharbor.modules.admin.entity.AdminOperationLog;
import com.mindharbor.modules.admin.service.AdminOperationLogService;
import com.mindharbor.modules.notification.dto.NotificationPushRequest;
import com.mindharbor.modules.notification.entity.Notification;
import com.mindharbor.modules.notification.mapper.NotificationMapper;
import com.mindharbor.modules.notification.mapper.NotificationReadMapper;
import com.mindharbor.modules.notification.service.NotificationService;
import com.mindharbor.modules.notification.vo.NotificationVO;
import com.mindharbor.modules.user.mapper.UserMapper;
import com.mindharbor.modules.user.vo.PageResult;
import com.mindharbor.security.CurrentUserProvider;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.stereotype.Service;

/** 通知业务实现：可见性、过期与已读判定都在 service 层完成。 */
@Service
public class NotificationServiceImpl implements NotificationService {

    private static final int DEFAULT_PAGE_SIZE = 20;

    private static final int MAX_PAGE_SIZE = 50;

    private static final long DEFAULT_EXPIRE_DAYS = 30L;

    private static final String NOTIFICATION_TARGET = "notification";

    private final NotificationMapper mapper;

    private final NotificationReadMapper readMapper;

    private final UserMapper userMapper;

    private final AdminOperationLogService operationLogService;

    private final CurrentUserProvider currentUserProvider;

    public NotificationServiceImpl(
            NotificationMapper mapper,
            NotificationReadMapper readMapper,
            UserMapper userMapper,
            AdminOperationLogService operationLogService,
            CurrentUserProvider currentUserProvider) {
        this.mapper = mapper;
        this.readMapper = readMapper;
        this.userMapper = userMapper;
        this.operationLogService = operationLogService;
        this.currentUserProvider = currentUserProvider;
    }

    @Override
    public PageResult<NotificationVO> list(int page, int size) {
        Long userId = currentUserProvider.getUserId();
        Instant now = Instant.now();
        int safePage = Math.max(page, 0);
        int safeSize = normalizeSize(size);
        long total = mapper.countVisible(userId, now);
        List<NotificationVO> items = mapper
                .findVisible(userId, now, safeSize, safePage * safeSize)
                .stream()
                .map(this::toView)
                .toList();
        return new PageResult<>(items, safePage, safeSize, total, totalPages(total, safeSize));
    }

    @Override
    public long unreadCount() {
        return mapper.countUnread(currentUserProvider.getUserId(), Instant.now());
    }

    @Override
    public void markRead(long notificationId) {
        Long userId = currentUserProvider.getUserId();
        Notification notification = mapper.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("通知不存在或已过期"));
        if (!isVisibleTo(notification, userId)) {
            throw new ResourceNotFoundException("通知不存在或已过期");
        }
        readMapper.markRead(notificationId, userId, Instant.now());
    }

    @Override
    public void markAllRead() {
        readMapper.markAllRead(currentUserProvider.getUserId(), Instant.now());
    }

    @Override
    public NotificationVO push(NotificationPushRequest request) {
        Notification notification = new Notification();
        notification.setScope(request.getScope());
        notification.setTargetUserId(resolveTargetUserId(request));
        notification.setTitle(request.getTitle().trim());
        notification.setContent(request.getContent().trim());
        notification.setCreatedBy(currentUserProvider.getUserId());
        notification.setExpireAt(resolveExpireAt(request));
        mapper.insert(notification);
        operationLogService.record(
                AdminOperationLog.Type.NOTIFICATION,
                NOTIFICATION_TARGET,
                notification.getId(),
                describeTarget(notification));
        return toView(notification);
    }

    @Override
    public PageResult<NotificationVO> adminList(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = normalizeSize(size);
        long total = mapper.countAll();
        List<NotificationVO> items = mapper
                .findPage(safeSize, safePage * safeSize)
                .stream()
                .map(this::toView)
                .toList();
        return new PageResult<>(items, safePage, safeSize, total, totalPages(total, safeSize));
    }

    @Override
    public void revoke(long notificationId) {
        mapper.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("通知不存在"));
        mapper.deleteById(notificationId);
        operationLogService.record(
                AdminOperationLog.Type.NOTIFICATION,
                NOTIFICATION_TARGET,
                notificationId,
                "撤回通知");
    }

    private Long resolveTargetUserId(NotificationPushRequest request) {
        if (request.getScope() == Notification.Scope.ALL) {
            return null;
        }
        if (request.getUserId() == null) {
            throw new IllegalArgumentException("定向推送必须指定用户");
        }
        userMapper.findById(request.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("用户不存在"));
        return request.getUserId();
    }

    /** permanent 为 true 时永不过期；未提供 expireAt 时默认 30 天后过期。 */
    private Instant resolveExpireAt(NotificationPushRequest request) {
        if (Boolean.TRUE.equals(request.getPermanent())) {
            return null;
        }
        Instant expireAt = request.getExpireAt();
        if (expireAt == null) {
            return Instant.now().plus(DEFAULT_EXPIRE_DAYS, ChronoUnit.DAYS);
        }
        if (!expireAt.isAfter(Instant.now())) {
            throw new IllegalArgumentException("过期时间必须晚于当前时间");
        }
        return expireAt;
    }

    private boolean isVisibleTo(Notification notification, Long userId) {
        Instant expireAt = notification.getExpireAt();
        if (expireAt != null && !expireAt.isAfter(Instant.now())) {
            return false;
        }
        if (notification.getScope() == Notification.Scope.ALL) {
            return true;
        }
        return userId.equals(notification.getTargetUserId());
    }

    private String describeTarget(Notification notification) {
        return notification.getScope() == Notification.Scope.ALL
                ? "推送全体通知"
                : "推送定向通知给用户 " + notification.getTargetUserId();
    }

    private NotificationVO toView(Notification notification) {
        return new NotificationVO(
                notification.getId(),
                notification.getScope() == null ? null : notification.getScope().name(),
                notification.getTitle(),
                notification.getContent(),
                notification.getCreatedAt(),
                notification.getExpireAt(),
                notification.getReadAt() != null);
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
