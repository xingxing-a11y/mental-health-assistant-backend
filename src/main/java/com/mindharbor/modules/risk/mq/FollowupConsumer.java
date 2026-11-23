package com.mindharbor.modules.risk.mq;

import com.mindharbor.modules.notification.service.NotificationService;
import com.mindharbor.modules.risk.entity.CareFollowup;
import com.mindharbor.modules.risk.event.RiskAlertEvent;
import com.mindharbor.modules.risk.mapper.CareFollowupMapper;
import com.mindharbor.modules.risk.mapper.RiskAlertConsumptionMapper;
import com.mindharbor.modules.user.entity.User;
import com.mindharbor.modules.user.mapper.UserMapper;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 24 小时延迟到期的关怀回访。
 *
 * <p>队列只是"闹钟"，任务真源是 {@code care_followups}：延迟消息丢了，兜底扫描任务仍会按
 * {@code status=PENDING AND due_at <= now} 补发。所以这里每次都要先查表确认状态，
 * 不能收到消息就直接发。
 */
@Component
@Lazy(false)
public class FollowupConsumer {

    private static final Logger log = LoggerFactory.getLogger(FollowupConsumer.class);

    static final String CONSUMER = "FOLLOWUP";

    private final RiskAlertConsumptionMapper consumptionMapper;

    private final CareFollowupMapper careFollowupMapper;

    private final UserMapper userMapper;

    private final NotificationService notificationService;

    public FollowupConsumer(
            RiskAlertConsumptionMapper consumptionMapper,
            CareFollowupMapper careFollowupMapper,
            UserMapper userMapper,
            NotificationService notificationService) {
        this.consumptionMapper = consumptionMapper;
        this.careFollowupMapper = careFollowupMapper;
        this.userMapper = userMapper;
        this.notificationService = notificationService;
    }

    @RabbitListener(queues = RiskMqConfig.FOLLOWUP_DUE_QUEUE)
    @Transactional
    public void onMessage(RiskAlertEvent event) {
        if (consumptionMapper.insertIgnore(event.getAlertId(), CONSUMER, Instant.now()) == 0) {
            return;
        }

        CareFollowup followup = careFollowupMapper.findByAlertId(event.getAlertId()).orElse(null);
        if (followup == null) {
            log.warn("[risk-followup] followup row missing, alertId={}", event.getAlertId());
            return;
        }
        if (followup.getStatus() != CareFollowup.Status.PENDING) {
            // 已被兜底扫描或其他实例处理过。
            log.debug("[risk-followup] already {}, alertId={}",
                    followup.getStatus(), event.getAlertId());
            return;
        }

        User user = userMapper.findById(event.getUserId()).orElse(null);
        if (user == null || user.getStatus() != User.Status.ACTIVE) {
            // 用户已注销或停用：不再打扰，但要把任务结掉，否则兜底扫描会一直捞到它。
            careFollowupMapper.markSkipped(followup.getId());
            log.info("[risk-followup] skipped inactive user, alertId={}", event.getAlertId());
            return;
        }

        notificationService.pushSystem(
                event.getUserId(),
                "最近过得怎么样",
                "前几天记录里提到的那段时间，现在好一些了吗？"
                        + "如果还是不好受，随时可以回来聊聊，或者联系学校心理中心。"
                        + "这条消息只是提醒你，你并不是一个人。");
        careFollowupMapper.markSent(followup.getId(), Instant.now());
        log.info("[risk-followup] sent, alertId={}", event.getAlertId());
    }
}
