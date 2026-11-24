package com.mindharbor.modules.risk.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mindharbor.common.PageResult;
import com.mindharbor.common.ResourceNotFoundException;
import com.mindharbor.modules.risk.dto.RiskAlertHandleRequest;
import com.mindharbor.modules.risk.entity.HandleResult;
import com.mindharbor.modules.risk.entity.RiskAlert;
import com.mindharbor.modules.risk.event.RiskAlertEvent;
import com.mindharbor.modules.risk.mapper.RiskAlertDailyStatMapper;
import com.mindharbor.modules.risk.mapper.RiskAlertMapper;
import com.mindharbor.modules.risk.vo.RiskAlertVO;
import com.mindharbor.security.CurrentUserProvider;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 管理端列表与人工处理：分页不能溢出、处理要记录方式与处理人。 */
class AdminRiskAlertServiceImplTest {

    private static final long ADMIN_ID = 42L;

    private final List<Long> receivedOffsets = new ArrayList<>();

    private final List<Integer> receivedLimits = new ArrayList<>();

    private RiskAlert existingAlert;

    /** 记录 markHandled 收到的实参，供断言处理方式/备注/处理人。 */
    private Object[] handledArgs = new Object[0];

    @Test
    @DisplayName("超大页码：offset 用 long 相乘，不溢出成负数")
    void hugePageDoesNotOverflow() {
        service().list(Integer.MAX_VALUE, 20);

        long offset = receivedOffsets.get(0);
        assertTrue(offset > 0, "offset 溢出会变成负数，实际=" + offset);
        assertEquals((long) Integer.MAX_VALUE * 20, offset);
    }

    @Test
    @DisplayName("size 超过上限时夹到 50，避免一次拉爆")
    void sizeIsCapped() {
        service().list(0, 500);

        assertEquals(50, receivedLimits.get(0));
    }

    @Test
    @DisplayName("size 非法时回落到默认 20")
    void invalidSizeFallsBack() {
        service().list(0, 0);

        assertEquals(20, receivedLimits.get(0));
    }

    @Test
    @DisplayName("列表把实体转成 VO，并且不暴露 refs")
    void listMapsToVoWithoutRefs() {
        RiskAlert alert = new RiskAlert();
        alert.setId(1L);
        alert.setAlertId("chat:1");
        alert.setUserId(9L);
        alert.setSource(RiskAlertEvent.Source.CHAT_HIGH);
        alert.setLevel(RiskAlertEvent.Level.HIGH);
        alert.setSummary("命中了高危词");
        alert.setRefs("{\"messageId\":\"1\"}");
        alert.setPublishStatus(RiskAlert.PublishStatus.SENT);
        alert.setPublishAttempts(0);
        alert.setOccurredAt(Instant.parse("2026-11-23T02:00:00Z"));
        alert.setCreatedAt(Instant.parse("2026-11-23T02:00:00Z"));
        alert.setId(1L);

        PageResult<RiskAlertVO> page = serviceWith(List.of(alert)).list(0, 20);

        assertEquals(1, page.getItems().size());
        RiskAlertVO vo = page.getItems().get(0);
        assertEquals("chat:1", vo.alertId());
        assertEquals("CHAT_HIGH", vo.source());
        assertEquals("SENT", vo.publishStatus());
        // 记录是 record，字段固定，这里用字段数量确保没有塞进 refs 这类内部信息
        assertEquals(14, RiskAlertVO.class.getRecordComponents().length);
    }

    @Test
    @DisplayName("处理不存在的预警：抛 404 而不是静默成功")
    void handleMissingThrows() {
        existingAlert = null;
        RiskAlertHandleRequest request = new RiskAlertHandleRequest();
        request.setHandleResult(HandleResult.CONTACTED);

        assertThrows(ResourceNotFoundException.class, () -> service().handle(99L, request));
    }

    @Test
    @DisplayName("处理：写入方式、去空白的备注、当前管理员编号")
    void handleWritesResultNoteAndOperator() {
        existingAlert = new RiskAlert();
        RiskAlertHandleRequest request = new RiskAlertHandleRequest();
        request.setHandleResult(HandleResult.CONTACTED);
        request.setHandleNote("  已电话联系，情绪稳定  ");

        service().handle(1L, request);

        assertEquals(HandleResult.CONTACTED, handledArgs[1]);
        assertEquals("已电话联系，情绪稳定", handledArgs[2], "备注要去掉首尾空白");
        assertEquals(Long.valueOf(ADMIN_ID), handledArgs[3], "处理人取自当前登录管理员");
        assertNotNull(handledArgs[4], "要带上处理时间");
    }

    @Test
    @DisplayName("备注只填空白：存 null，不存空串")
    void blankNoteNormalizedToNull() {
        existingAlert = new RiskAlert();
        RiskAlertHandleRequest request = new RiskAlertHandleRequest();
        request.setHandleResult(HandleResult.OBSERVING);
        request.setHandleNote("   ");

        service().handle(1L, request);

        assertNull(handledArgs[2]);
    }

    private AdminRiskAlertServiceImpl service() {
        return serviceWith(List.of());
    }

    private static CurrentUserProvider admin(long userId) {
        return new CurrentUserProvider() {
            @Override
            public Optional<Long> findUserId() {
                return Optional.of(userId);
            }
        };
    }

    private AdminRiskAlertServiceImpl serviceWith(List<RiskAlert> rows) {
        RiskAlertMapper alertMapper = (RiskAlertMapper) Proxy.newProxyInstance(
                RiskAlertMapper.class.getClassLoader(),
                new Class<?>[] {RiskAlertMapper.class},
                (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return null;
                    }
                    return switch (method.getName()) {
                        case "findPage" -> {
                            receivedLimits.add((Integer) args[0]);
                            receivedOffsets.add((Long) args[1]);
                            yield rows;
                        }
                        case "countAll" -> (long) rows.size();
                        case "selectById" -> existingAlert;
                        case "markHandled" -> {
                            handledArgs = args;
                            yield 1;
                        }
                        default -> null;
                    };
                });
        RiskAlertDailyStatMapper statMapper = (RiskAlertDailyStatMapper) Proxy.newProxyInstance(
                RiskAlertDailyStatMapper.class.getClassLoader(),
                new Class<?>[] {RiskAlertDailyStatMapper.class},
                (proxy, method, args) -> null);
        return new AdminRiskAlertServiceImpl(alertMapper, statMapper, admin(ADMIN_ID));
    }
}
