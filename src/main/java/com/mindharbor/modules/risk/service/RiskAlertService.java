package com.mindharbor.modules.risk.service;

import com.mindharbor.modules.risk.event.RiskAlertEvent;
import java.time.LocalDate;

/** 风险事件发布入口。调用方只管调，投递失败不会向外抛异常。 */
public interface RiskAlertService {

    /**
     * 发布一条风险事件。
     *
     * <p>先把事件写入 {@code risk_alerts}（该表同时是发布 outbox），再在事务提交后投递；
     * 写入撞唯一键说明这条事件已经发过，直接返回 false。
     *
     * @return true 表示本次是新事件，false 表示重复事件已被幂等拦下
     */
    boolean publish(RiskAlertEvent event);

    /**
     * 补偿重投：只投递，不写库。
     *
     * <p>补偿任务必须走这条路径——如果调 {@link #publish}，第一句 insert 就会撞唯一键然后直接返回，
     * 消息永远发不出去。
     */
    void republish(RiskAlertEvent event);

    /**
     * 对话命中高危词。
     *
     * <p>调用方是 SSE 路径上的 HIGH 分支，必须保证即使这里出问题也不影响危机提示返回。
     *
     * @return true 表示这次是新事件
     */
    boolean publishChatHighRisk(Long userId, long messageId, long sessionId);

    /**
     * 测评标准分达到阈值。
     *
     * <p>是否达到阈值由调用方判定（业务判断留在业务侧），这里只负责组装与投递。
     *
     * @param standardScore 0~100 的标准分，仅用于事件描述
     */
    boolean publishAssessmentHighRisk(Long userId, long resultId, int standardScore);

    /**
     * 连续多日负面日记。
     *
     * @param day 判定基准日（东八区自然日），决定幂等键
     */
    boolean publishDiaryStreak(Long userId, LocalDate day, int negativeDays);
}
