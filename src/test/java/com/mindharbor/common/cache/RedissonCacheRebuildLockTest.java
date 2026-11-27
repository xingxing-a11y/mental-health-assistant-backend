package com.mindharbor.common.cache;

import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/** Redisson 适配层的边界测试：local 模式不得创建/使用分布式锁。 */
class RedissonCacheRebuildLockTest {

    @Test
    void localModeReturnsNullAndDoesNotConnect() throws Exception {
        RedissonCacheRebuildLock lock = new RedissonCacheRebuildLock(
                "local", "127.0.0.1", 6379, "", 0, 200);

        assertNull(lock.tryAcquire("mindharbor:cache:article:list", 10));
        lock.shutdown();
    }
}
