package com.mindharbor.modules.risk.mq;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

/**
 * 启动时主动声明 MQ 拓扑。
 *
 * <p>为什么不能依赖 Spring Boot 自动配置的 RabbitAdmin：全局 {@code spring.main.lazy-initialization=true}
 * 会让它一直不被创建，队列与交换机也就永远不会声明。表现极其隐蔽——应用启动一切正常，
 * 而第一条消息因为 exchange 不存在被直接丢弃。
 *
 * <p>为什么不靠 {@code RabbitAdmin.afterPropertiesSet()} 的自动发现：它内部走
 * {@code getBeansOfType(Declarable.class)}，在拿不到声明对象时会<strong>静默返回</strong>，
 * 既不报错也不打日志——看起来"声明成功"，实际什么都没做。这里改为显式逐个声明，
 * 失败一定抛得出来。
 */
@Component
public class RiskTopologyInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RiskTopologyInitializer.class);

    private final ConnectionFactory connectionFactory;

    private final ApplicationContext applicationContext;

    private final List<Exchange> exchanges;

    private final List<Queue> queues;

    private final List<Binding> bindings;

    /**
     * 历史遗留绑定：延迟队列早期用 {@code risk.alert.*} 通配，导致对话高危也进队列排队 24 小时。
     *
     * <p>只改代码里的绑定声明不会让 broker 上已有的绑定消失，必须显式解绑。
     */
    private static final Binding LEGACY_FOLLOWUP_DELAY_BINDING = new Binding(
            RiskMqConfig.FOLLOWUP_DELAY_QUEUE,
            Binding.DestinationType.QUEUE,
            RiskMqConfig.EXCHANGE,
            RiskMqConfig.RK_ALERT_ALL,
            null);

    public RiskTopologyInitializer(
            ConnectionFactory connectionFactory,
            ApplicationContext applicationContext,
            List<Exchange> exchanges,
            List<Queue> queues,
            List<Binding> bindings) {
        this.connectionFactory = connectionFactory;
        this.applicationContext = applicationContext;
        this.exchanges = exchanges;
        this.queues = queues;
        this.bindings = bindings;
    }

    @Override
    public void run(ApplicationArguments args) {
        RabbitAdmin admin = new RabbitAdmin(connectionFactory);
        admin.setApplicationContext(applicationContext);

        // 显式声明阶段必须让异常抛出来。ignoreDeclarationExceptions=true 会把失败吞成一条
        // "continuing..." 的 WARN 后正常返回，调用方看到的是"声明完成"，实际什么都没做。
        admin.setIgnoreDeclarationExceptions(false);
        try {
            declare(admin, exchanges, queues, bindings);
            removeLegacyFollowupDelayBinding(admin);
            log.info("[risk-mq] topology declared, exchanges={}, queues={}, bindings={}",
                    exchanges.size(), queues.size(), bindings.size());
        } catch (RuntimeException exception) {
            log.warn("[risk-mq] topology declaration failed, broker likely unavailable;"
                    + " queue/exchange must be declared before the first publish", exception);
        }

        // 注册 ConnectionListener 时切回宽松模式：之后每次重连的声明失败只记日志，不抛进容器。
        admin.setIgnoreDeclarationExceptions(true);
        admin.afterPropertiesSet();
    }

    /** 顺序不能反：binding 依赖 exchange 与 queue 都已存在。 */
    static void declare(
            AmqpAdmin admin, List<Exchange> exchanges, List<Queue> queues, List<Binding> bindings) {
        exchanges.forEach(admin::declareExchange);
        queues.forEach(admin::declareQueue);
        bindings.forEach(admin::declareBinding);
    }

    /**
     * 解绑延迟队列的历史通配绑定。
     *
     * <p>放在声明之后：新绑定先就位，再移除旧的，中间不会出现「测评/日记事件无处可去」的窗口。
     * AMQP 对不存在的绑定执行 unbind 是空操作，因此每次启动都跑一遍即可，无需额外的迁移脚本。
     *
     * <p>失败只记日志：这一步是清理动作，不该阻断启动——最坏情况是队列里继续多收对话事件，
     * 24 小时后被消费者丢弃，不影响任何用户可见行为。
     */
    static void removeLegacyFollowupDelayBinding(AmqpAdmin admin) {
        try {
            admin.removeBinding(LEGACY_FOLLOWUP_DELAY_BINDING);
        } catch (RuntimeException exception) {
            log.warn("[risk-mq] remove legacy wildcard binding failed, queue={}",
                    RiskMqConfig.FOLLOWUP_DELAY_QUEUE, exception);
        }
    }
}
