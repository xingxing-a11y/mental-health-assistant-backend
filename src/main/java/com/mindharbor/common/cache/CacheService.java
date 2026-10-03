package com.mindharbor.common.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 单体缓存组件：提供 JSON 缓存、本地单飞锁和提交后失效所需的基础操作。
 *
 * <p>缓存内容一律是可重建的派生数据，MySQL 仍是唯一真实数据源；Redis 读写失败时向外抛出的结果
 * 等同于未命中，由调用方回源数据库，保证缓存故障不影响主业务。
 *
 * <p>单飞锁只作用于当前 JVM：多实例部署后同一时刻不同实例仍可能同时回源，需要替换为经过验证的
 * Redis 分布式锁。本阶段不实现分布式锁，也不手写 Redis 锁。
 */
@Service
public class CacheService {

    private static final Logger log = LoggerFactory.getLogger(CacheService.class);

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final CacheProperties cacheProperties;
    private final ConcurrentMap<String, LockEntry> locks = new ConcurrentHashMap<>();

    public CacheService(StringRedisTemplate stringRedisTemplate, CacheProperties cacheProperties) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = CacheJsonMapper.create();
        this.cacheProperties = cacheProperties;
    }

    /**
     * 读取缓存并反序列化；未命中返回 {@code null}。
     *
     * <p>反序列化失败或 Redis 读取失败都按未命中处理，并尝试删除损坏的键；删除失败也不影响调用方回源。
     */
    public <T> T get(String key, Class<T> type) {
        try {
            String value = stringRedisTemplate.opsForValue().get(key);
            return value == null ? null : objectMapper.readValue(value, type);
        } catch (Exception exception) {
            logCacheFailure("read", key, exception);
            delete(key);
            return null;
        }
    }

    public <T> T get(String key, TypeReference<T> type) {
        try {
            String value = stringRedisTemplate.opsForValue().get(key);
            return value == null ? null : objectMapper.readValue(value, type);
        } catch (Exception exception) {
            logCacheFailure("read", key, exception);
            delete(key);
            return null;
        }
    }

    public boolean exists(String key) {
        try {
            return Boolean.TRUE.equals(stringRedisTemplate.hasKey(key));
        } catch (RuntimeException exception) {
            log.warn("Cache existence check failed, key={}", key, exception);
            return false;
        }
    }

    public void put(String key, Object value, int ttlSeconds, int jitterSeconds) {
        try {
            String json = objectMapper.writeValueAsString(value);
            stringRedisTemplate.opsForValue().set(key, json, Duration.ofSeconds(ttl(ttlSeconds, jitterSeconds)));
        } catch (JsonProcessingException | RuntimeException exception) {
            logCacheFailure("write", key, exception);
        }
    }

    public void delete(String key) {
        try {
            stringRedisTemplate.delete(key);
        } catch (RuntimeException exception) {
            log.warn("Cache delete failed, key={}", key, exception);
        }
    }

    public void deleteWithRetry(String... keys) {
        for (String key : keys) {
            // 短路求值：第一次成功就不再执行第二次，两次都失败才放弃
            boolean deleted = deleteOnce(key) || deleteOnce(key);
            if (!deleted) {
                log.warn("Cache delete gave up after one retry, key={}, rely on TTL to recover", key);
            }
        }
    }

    public void deleteAfterCommit(String... keys) {
        //当前线程没有事务，立刻删
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            deleteWithRetry(keys);
            return;
        }
        //当前线程有事务，登记一个"提交后执行"的回调
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                deleteWithRetry(keys);
            }
        });
    }

    /**
     * 按缓存键执行单飞回源，避免同一个键的并发未命中一起压到 MySQL，避免缓存击穿。
     *
     * <p>同一 JVM 内相同键的请求会依次通过：拿到锁的请求先二次读取缓存，仍然未命中才执行回源；
     * 拿不到锁的请求等待不超过 {@code cache.rebuild-lock.wait-millis}，随后重新读取缓存，
     * 只有缓存依然为空时才直接回源。这样既保证正常情况下一个键只回源一次，又不会让请求无限阻塞。
     *
     * @param key               缓存键，锁按该键隔离，不使用全局锁
     * @param cachedValueReader 读取缓存并转换成返回值的方法，未命中返回 {@code null}
     * @param loader            回源方法，仅在缓存未命中时执行
     */
    public <T> T loadWithSingleFlight(String key, Supplier<T> cachedValueReader, Supplier<T> loader) {
        //领取锁条目，引用计数 +1
        LockEntry entry = acquireLockEntry(key);
        boolean locked = false;
        try {
            locked = tryLock(entry.lock);
            T cached = cachedValueReader.get();
            if (cached != null) {
                return cached;
            }
            return loader.get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            T cached = cachedValueReader.get();
            return cached != null ? cached : loader.get();
        } finally {
            if (locked) {
                entry.lock.unlock();
            }
            releaseLockEntry(key, entry);
        }
    }

    private boolean tryLock(ReentrantLock lock) throws InterruptedException {
        return lock.tryLock(cacheProperties.rebuildLock().waitMillis(), TimeUnit.MILLISECONDS);
    }

    /** 领取键对应的锁条目并累加引用计数，保证锁对象在有人使用期间不会被并发替换。 */
    private LockEntry acquireLockEntry(String key) {
        return locks.compute(key, (ignored, existing) -> {
            LockEntry entry = existing == null ? new LockEntry() : existing;
            entry.references++;
            return entry;
        });
    }

    /** 释放锁条目引用，最后一个引用释放后移除条目，避免按文章编号无限累积锁对象。 */
    private void releaseLockEntry(String key, LockEntry entry) {
        locks.computeIfPresent(key, (ignored, existing) -> {
            if (existing != entry) {
                return existing;
            }
            entry.references--;
            return entry.references == 0 ? null : entry;
        });
    }

    private boolean deleteOnce(String key) {
        try {
            stringRedisTemplate.delete(key);
            return true;
        } catch (RuntimeException exception) {
            log.debug("Cache delete attempt failed, key={}", key, exception);
            return false;
        }
    }

    /**
     * 记录缓存读写失败。
     *
     * <p>缓存正文包含文章、公告或敏感词内容，异常信息可能回显出错字段的取值，
     * 因此 WARN 只记录异常类型，完整堆栈放到 DEBUG，避免日志泄露业务数据。
     */
    private void logCacheFailure(String action, String key, Exception exception) {
        log.warn("Cache {} failed, key={}, error={}", action, key, exception.getClass().getSimpleName());
        log.debug("Cache {} failure detail, key={}", action, key, exception);
    }

    private int ttl(int base, int jitter) {
        return base + (jitter == 0 ? 0 : ThreadLocalRandom.current().nextInt(jitter + 1));
    }

    /** 键级锁条目；引用计数只在 map 的 compute 回调里改动，避免等待中的请求拿到已失效的锁对象。 */
    private static final class LockEntry {

        private final ReentrantLock lock = new ReentrantLock();

        private int references;
    }
}
