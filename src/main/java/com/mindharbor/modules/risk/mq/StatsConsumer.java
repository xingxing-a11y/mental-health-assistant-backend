package com.mindharbor.modules.risk.mq;

import com.mindharbor.modules.risk.event.RiskAlertEvent;
import com.mindharbor.modules.risk.mapper.RiskAlertConsumptionMapper;
import com.mindharbor.modules.risk.mapper.RiskAlertDailyStatMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 风险事件 → 按日统计。
 *
 * <p>统计日期取<strong>事件发生日</strong>而不是消费当天：延迟消息 24 小时后才被消费，
 * 用消费日会让整条链的统计整体偏移一天。
 */
@Component
@Lazy(false)
public class StatsConsumer {

    private static final Logger log = LoggerFactory.getLogger(StatsConsumer.class);

    static final String CONSUMER = "STATS";

    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Shanghai");

    private final RiskAlertConsumptionMapper consumptionMapper;

    private final RiskAlertDailyStatMapper dailyStatMapper;

    public StatsConsumer(
            RiskAlertConsumptionMapper consumptionMapper,
            RiskAlertDailyStatMapper dailyStatMapper) {
        this.consumptionMapper = consumptionMapper;
        this.dailyStatMapper = dailyStatMapper;
    }

    @RabbitListener(queues = RiskMqConfig.STATS_QUEUE)
    @Transactional
    public void onMessage(RiskAlertEvent event) {
        if (consumptionMapper.insertIgnore(event.getAlertId(), CONSUMER, Instant.now()) == 0) {
            return;
        }
        dailyStatMapper.increment(
                statDateOf(event), event.getSource().name(), Instant.now());
        log.info("[risk-stats] counted, alertId={}, source={}", event.getAlertId(), event.getSource());
    }

    /** 按东八区折算自然日，与日记连击判定用同一个时区口径。 */
    static LocalDate statDateOf(RiskAlertEvent event) {
        return LocalDate.ofInstant(event.getOccurredAt(), APP_ZONE);
    }
}
