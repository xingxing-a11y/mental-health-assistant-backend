package com.mindharbor.common.cache;

import jakarta.annotation.PreDestroy;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.redisson.Redisson;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.client.RedisException;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 基于 Redisson {@link RLock} 的缓存重建分布式锁。
 *
 * <p>锁按线程归属，{@code loader} 必须在调用 {@link #tryAcquire} 的同一线程执行；
 * 当前业务全同步，满足这个前提。
 *
 * <p>Redis 不可用时返回 {@code null}，由 {@link CacheService} 降级到本地锁。此时多实例
 * 只能做到“每实例最多 1 个回源”，不能宣称仍是全局 K=1——这是可用性优先的已知边界。
 */
@Component
public class RedissonCacheRebuildLock implements CacheRebuildLock {

    private static final Logger log = LoggerFactory.getLogger(RedissonCacheRebuildLock.class);

    private static final String LOCK_PREFIX = "mindharbor:lock:cache:";
    private static final long RETRY_BACKOFF_MILLIS = 10_000L;

    private final String mode;
    private final String host;
    private final int port;
    private final String password;
    private final int database;
    private final int connectTimeoutMillis;

    private final Object monitor = new Object();
    private volatile RedissonClient client;
    private volatile long retryAfterMillis;

    public RedissonCacheRebuildLock(
            @Value("${cache.lock.mode:redisson}") String mode,
            @Value("${spring.data.redis.host:localhost}") String host,
            @Value("${spring.data.redis.port:6379}") int port,
            @Value("${spring.data.redis.password:}") String password,
            @Value("${spring.data.redis.database:0}") int database,
            @Value("${cache.lock.connect-timeout-millis:500}") int connectTimeoutMillis) {
        this.mode = mode;
        this.host = host;
        this.port = port;
        this.password = password;
        this.database = database;
        this.connectTimeoutMillis = Math.max(connectTimeoutMillis, 100);
    }

    @Override
    public CacheRebuildLockHandle tryAcquire(String cacheKey, long waitMillis)
            throws InterruptedException {
        if (!"redisson".equalsIgnoreCase(mode)) {
            return null;
        }
        if (System.currentTimeMillis() < retryAfterMillis) {
            return null;
        }
        long deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(waitMillis);
        RedissonClient current = client();
        if (current == null) {
            return null;
        }
        RLock lock;
        try {
            lock = current.getLock(LOCK_PREFIX + cacheKey);
        } catch (RedisException exception) {
            log.warn("Redisson getLock failed, fallback to local lock, key={}, error={}",
                    cacheKey, exception.getClass().getSimpleName());
            return null;
        }
        boolean acquired;
        try {
            // 两参重载不传 leaseTime，启用 watchdog 自动续期；client() 建连可能已耗掉一部分预算
            acquired = lock.tryLock(remainingMillis(deadlineNanos), TimeUnit.MILLISECONDS);
            retryAfterMillis = 0;
        } catch (InterruptedException exception) {
            throw exception;
        } catch (RedisException exception) {
            retryAfterMillis = System.currentTimeMillis() + RETRY_BACKOFF_MILLIS;
            bestEffortUnlock(lock, cacheKey);
            log.warn("Redisson tryLock failed, fallback to local lock, key={}, error={}",
                    cacheKey, exception.getClass().getSimpleName());
            return null;
        }
        return new RedissonLockHandle(lock, acquired, cacheKey);
    }

    private long remainingMillis(long deadlineNanos) {
        return Math.max(0, TimeUnit.NANOSECONDS.toMillis(deadlineNanos - System.nanoTime()));
    }

    private RedissonClient client() {
        RedissonClient current = client;
        if (current != null) {
            return current;
        }
        if (System.currentTimeMillis() < retryAfterMillis) {
            return null;
        }
        synchronized (monitor) {
            if (client != null) {
                return client;
            }
            if (System.currentTimeMillis() < retryAfterMillis) {
                return null;
            }
            try {
                Config config = new Config();
                SingleServerConfig server = config.useSingleServer()
                        .setAddress("redis://" + host + ":" + port)
                        .setDatabase(database)
                        .setConnectionMinimumIdleSize(1)
                        .setConnectionPoolSize(4)
                        .setConnectTimeout(connectTimeoutMillis)
                        .setTimeout(connectTimeoutMillis)
                        .setRetryAttempts(1)
                        .setRetryInterval(200);
                if (password != null && !password.isBlank()) {
                    server.setPassword(password);
                }
                // 只做锁，默认 16/32 个线程对 2C2G 太重
                config.setThreads(2).setNettyThreads(2);
                client = Redisson.create(config);
                log.info("Redisson lock client initialized, host={}, port={}, database={}",
                        host, port, database);
                return client;
            } catch (RedisException exception) {
                retryAfterMillis = System.currentTimeMillis() + RETRY_BACKOFF_MILLIS;
                log.warn("Redisson unavailable, fallback to local lock for {} ms, error={}",
                        RETRY_BACKOFF_MILLIS, exception.getClass().getSimpleName());
                return null;
            }
        }
    }

    private void bestEffortUnlock(RLock lock, String cacheKey) {
        try {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        } catch (RuntimeException exception) {
            log.warn("Redisson best-effort unlock failed, key={}, error={}",
                    cacheKey, exception.getClass().getSimpleName());
        }
    }

    @PreDestroy
    public void shutdown() {
        RedissonClient current = client;
        client = null;
        if (current == null) {
            return;
        }
        try {
            current.shutdown();
        } catch (RuntimeException exception) {
            log.warn("Redisson shutdown failed, error={}", exception.getClass().getSimpleName());
        }
    }

    private static final class RedissonLockHandle implements CacheRebuildLockHandle {

        private final RLock lock;
        private final boolean acquired;
        private final String cacheKey;
        private final AtomicBoolean released = new AtomicBoolean(false);

        private RedissonLockHandle(RLock lock, boolean acquired, String cacheKey) {
            this.lock = lock;
            this.acquired = acquired;
            this.cacheKey = cacheKey;
        }

        @Override
        public boolean isAcquired() {
            return acquired;
        }

        @Override
        public void release() {
            if (!released.compareAndSet(false, true) || !acquired) {
                return;
            }
            try {
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            } catch (RuntimeException exception) {
                log.warn("Redisson unlock failed, key={}, error={}",
                        cacheKey, exception.getClass().getSimpleName());
            }
        }
    }
}
