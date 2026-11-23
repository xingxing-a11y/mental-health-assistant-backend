package com.mindharbor.modules.risk.service;

import com.mindharbor.modules.risk.event.RiskAlertEvent;

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
}
