package com.mindharbor.modules.risk.mq;

import com.mindharbor.modules.notification.service.NotificationService;
import com.mindharbor.modules.risk.event.RiskAlertEvent;
import com.mindharbor.modules.risk.mapper.RiskAlertConsumptionMapper;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 风险事件 → 站内信。
 *
 * <p>幂等记录与发通知在<strong>同一事务</strong>：先插消费记录，插不进去说明同一条事件已被这个消费者处理过；
 * 业务失败会连消费记录一起回滚，重试时还能重新进入。
 *
 * <p>{@code @Lazy(false)} 不可省：全局 {@code spring.main.lazy-initialization=true} 下 Bean 不被创建，
 * {@code RabbitListenerAnnotationBeanPostProcessor} 就扫不到 {@code @RabbitListener}，
 * 表现是应用启动正常、broker 上却没有任何消费者。
 */
@Component
@Lazy(false)
public class NotifyConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotifyConsumer.class);

    /** 与 risk_alert_consumptions.consumer 对应。 */
    static final String CONSUMER = "NOTIFY";

    private final RiskAlertConsumptionMapper consumptionMapper;

    private final NotificationService notificationService;

    public NotifyConsumer(
            RiskAlertConsumptionMapper consumptionMapper,
            NotificationService notificationService) {
        this.consumptionMapper = consumptionMapper;
        this.notificationService = notificationService;
    }

    @RabbitListener(queues = RiskMqConfig.NOTIFY_QUEUE)
    @Transactional
    public void onMessage(RiskAlertEvent event) {
        if (consumptionMapper.insertIgnore(event.getAlertId(), CONSUMER, Instant.now()) == 0) {
            log.debug("[risk-notify] duplicated, alertId={}", event.getAlertId());
            return;
        }
        notificationService.pushSystem(
                event.getUserId(), titleOf(event), contentOf(event));
        log.info("[risk-notify] pushed, alertId={}", event.getAlertId());
    }

    /** 措辞原则：不诊断、不说教、给出可执行的下一步。 */
    static String titleOf(RiskAlertEvent event) {
        return switch (event.getSource()) {
            case CHAT_HIGH -> "有些话我们想认真回应你";
            case ASSESSMENT_HIGH -> "最近一次测评的分数偏高";
            case DIARY_STREAK -> "这几天看起来都不太轻松";
        };
    }

    static String contentOf(RiskAlertEvent event) {
        return switch (event.getSource()) {
            case CHAT_HIGH ->
                    "你刚才提到的事情很重要。如果你愿意，可以随时回来继续说，"
                            + "也可以联系信任的人或拨打心理援助热线。"
                            + "我们不是医生，不能做诊断，但会一直在这里。";
            case ASSESSMENT_HIGH ->
                    "这不是诊断，只是一次自评的提示。建议过几天在状态平稳时再测一次，"
                            + "或者找信任的人聊聊最近的感受。"
                            + "如果这种状态持续，可以联系学校心理中心或专业机构。";
            case DIARY_STREAK ->
                    "连续几天都写下低落或焦虑的情绪，说明这段时间对你来说不容易。"
                            + "把它们写下来本身就需要力气。"
                            + "要不要试着挑一件小事和身边的人说说？"
                            + "如果愿意，也可以回来和我聊聊。";
        };
    }
}
