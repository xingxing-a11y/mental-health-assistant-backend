package com.mindharbor.modules.risk.mq;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;

/** 拓扑声明的调用顺序与失败传播。项目约定不引入 Mockito，这里用动态代理造假实现。 */
class RiskTopologyInitializerTest {

    @Test
    @DisplayName("声明顺序必须是 exchange → queue → binding，否则绑定会因目标不存在而失败")
    void declaresInDependencyOrder() {
        List<String> calls = new ArrayList<>();
        AmqpAdmin admin = fakeAdmin(calls, null);

        RiskTopologyInitializer.declare(
                admin,
                List.of(exchange()),
                List.of(queue()),
                List.of(binding()));

        assertEquals(List.of("declareExchange", "declareQueue", "declareBinding"), calls);
    }

    @Test
    @DisplayName("声明失败必须往外抛，不能被吞掉后假装成功")
    void failureIsPropagated() {
        AmqpAdmin admin = fakeAdmin(new ArrayList<>(), new IllegalStateException("broker down"));

        IllegalStateException thrown = assertThrows(
                IllegalStateException.class,
                () -> RiskTopologyInitializer.declare(
                        admin, List.of(exchange()), List.of(queue()), List.of(binding())));

        assertEquals("broker down", thrown.getMessage());
    }

    private static Exchange exchange() {
        return new TopicExchange("test.exchange");
    }

    private static Queue queue() {
        return new Queue("test.queue");
    }

    private static Binding binding() {
        return new Binding("test.queue", Binding.DestinationType.QUEUE, "test.exchange", "rk", null);
    }

    private static AmqpAdmin fakeAdmin(List<String> calls, RuntimeException failure) {
        return (AmqpAdmin) Proxy.newProxyInstance(
                AmqpAdmin.class.getClassLoader(),
                new Class<?>[] {AmqpAdmin.class},
                (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return null;
                    }
                    calls.add(method.getName());
                    if (failure != null) {
                        throw failure;
                    }
                    return null;
                });
    }
}
