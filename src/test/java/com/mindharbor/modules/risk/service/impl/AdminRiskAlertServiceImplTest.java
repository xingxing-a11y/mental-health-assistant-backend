package com.mindharbor.modules.risk.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mindharbor.common.PageResult;
import com.mindharbor.common.ResourceNotFoundException;
import com.mindharbor.modules.risk.entity.RiskAlert;
import com.mindharbor.modules.risk.event.RiskAlertEvent;
import com.mindharbor.modules.risk.mapper.RiskAlertDailyStatMapper;
import com.mindharbor.modules.risk.mapper.RiskAlertMapper;
import com.mindharbor.modules.risk.vo.RiskAlertVO;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 管理端列表与归档：分页不能溢出、归档要区分"已处理"与"不存在"。 */
class AdminRiskAlertServiceImplTest {

    private final List<Long> receivedOffsets = new ArrayList<>();

    private final List<Integer> receivedLimits = new ArrayList<>();

    private RiskAlert existingAlert;

    private int archiveUpdates = 1;

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
        assertEquals(11, RiskAlertVO.class.getRecordComponents().length);
    }

    @Test
    @DisplayName("归档不存在的预警：抛 404 而不是静默成功")
    void archiveMissingThrows() {
        existingAlert = null;

        assertThrows(ResourceNotFoundException.class, () -> service().archive(99L));
    }

    @Test
    @DisplayName("归档已存在的预警：重复调用不报错（幂等）")
    void archiveIsIdempotent() {
        existingAlert = new RiskAlert();
        archiveUpdates = 0;   // 已经处理过，UPDATE 影响 0 行

        service().archive(1L);   // 不抛异常即通过
    }

    private AdminRiskAlertServiceImpl service() {
        return serviceWith(List.of());
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
                        case "markArchived" -> archiveUpdates;
                        default -> null;
                    };
                });
        RiskAlertDailyStatMapper statMapper = (RiskAlertDailyStatMapper) Proxy.newProxyInstance(
                RiskAlertDailyStatMapper.class.getClassLoader(),
                new Class<?>[] {RiskAlertDailyStatMapper.class},
                (proxy, method, args) -> null);
        return new AdminRiskAlertServiceImpl(alertMapper, statMapper);
    }
}
