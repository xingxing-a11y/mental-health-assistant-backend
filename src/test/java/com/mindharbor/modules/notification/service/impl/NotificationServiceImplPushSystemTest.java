package com.mindharbor.modules.notification.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mindharbor.modules.admin.service.AdminOperationLogService;
import com.mindharbor.modules.notification.entity.Notification;
import com.mindharbor.modules.notification.mapper.NotificationMapper;
import com.mindharbor.modules.notification.mapper.NotificationReadMapper;
import com.mindharbor.modules.user.mapper.UserMapper;
import com.mindharbor.security.CurrentUserProvider;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 系统级推送：MQ 消费线程里没有安全上下文，这条路必须完全不看身份。
 */
class NotificationServiceImplPushSystemTest {

    private final List<Notification> inserted = new ArrayList<>();

    private final List<Object> auditCalls = new ArrayList<>();

    private NotificationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new NotificationServiceImpl(
                fakeNotificationMapper(),
                (NotificationReadMapper) Proxy.newProxyInstance(
                        NotificationReadMapper.class.getClassLoader(),
                        new Class<?>[] {NotificationReadMapper.class},
                        (proxy, method, args) -> null),
                (UserMapper) Proxy.newProxyInstance(
                        UserMapper.class.getClassLoader(),
                        new Class<?>[] {UserMapper.class},
                        (proxy, method, args) -> null),
                (AdminOperationLogService) Proxy.newProxyInstance(
                        AdminOperationLogService.class.getClassLoader(),
                        new Class<?>[] {AdminOperationLogService.class},
                        (proxy, method, args) -> {
                            auditCalls.add(method.getName());
                            return null;
                        }),
                new CurrentUserProvider());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("没有登录用户时也能推送：消费线程正是这种状态")
    void worksWithoutSecurityContext() {
        SecurityContextHolder.clearContext();

        service.pushSystem(9L, "最近还好吗", "如果愿意，可以聊聊最近的感受。");

        assertEquals(1, inserted.size(), "消费线程里没有安全上下文，未登录也必须能写通知");
    }

    @Test
    @DisplayName("写入的字段：定向、无操作人、30 天后过期")
    void writesExpectedFields() {
        service.pushSystem(9L, "最近还好吗", "内容");

        Notification notification = inserted.get(0);
        assertEquals(Notification.Scope.USER, notification.getScope());
        assertEquals(Long.valueOf(9L), notification.getTargetUserId());
        assertEquals("最近还好吗", notification.getTitle());
        assertEquals("内容", notification.getContent());
        assertNull(notification.getCreatedBy(), "系统触发没有操作人，不能填当前用户");

        long days = Duration.between(Instant.now(), notification.getExpireAt()).toDays();
        assertTrue(days >= 29 && days <= 30, "默认 30 天后过期，实际=" + days + " 天");
    }

    @Test
    @DisplayName("系统推送不写管理操作日志：那条链路同样依赖操作人身份")
    void doesNotWriteAdminAuditLog() {
        service.pushSystem(9L, "标题", "内容");

        assertEquals(0, auditCalls.size(), "系统通知不是管理动作，且审计需要 operatorId");
    }

    @Test
    @DisplayName("超长标题与内容按列宽截断，不让整条通知写失败")
    void clipsOverlongText() {
        service.pushSystem(9L, "标".repeat(300), "内".repeat(2500));

        Notification notification = inserted.get(0);
        assertEquals(200, notification.getTitle().length());
        assertEquals(2000, notification.getContent().length());
    }

    @Test
    @DisplayName("接收人为空或内容为空属于参数错误，直接拒绝")
    void rejectsInvalidArguments() {
        assertThrows(IllegalArgumentException.class, () -> service.pushSystem(null, "标题", "内容"));
        assertThrows(IllegalArgumentException.class, () -> service.pushSystem(9L, " ", "内容"));
        assertThrows(IllegalArgumentException.class, () -> service.pushSystem(9L, "标题", ""));
    }

    @Test
    @DisplayName("有登录用户时也不会把当前用户当成接收人")
    void neverUsesCurrentUserAsTarget() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(1L, null, List.of()));

        service.pushSystem(9L, "标题", "内容");

        assertEquals(Long.valueOf(9L), inserted.get(0).getTargetUserId());
    }

    private NotificationMapper fakeNotificationMapper() {
        return (NotificationMapper) Proxy.newProxyInstance(
                NotificationMapper.class.getClassLoader(),
                new Class<?>[] {NotificationMapper.class},
                (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return null;
                    }
                    if ("insert".equals(method.getName())) {
                        inserted.add((Notification) args[0]);
                        return 1;
                    }
                    return null;
                });
    }
}
