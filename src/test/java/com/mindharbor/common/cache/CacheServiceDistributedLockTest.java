package com.mindharbor.common.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

/** 分布式重建锁的行为测试：用假锁证明 CacheService 只让持锁者回源。 */
class CacheServiceDistributedLockTest {

    private static final String KEY = "mindharbor:cache:article:list";

    private static final CacheProperties PROPERTIES = new CacheProperties(
            new CacheProperties.ArticleEntry(3600, 600, 300, 60),
            new CacheProperties.Entry(3600, 600),
            new CacheProperties.Entry(1200, 300),
            new CacheProperties.RebuildLock(200));

    @Test
    void distributedLockAcquiredLoadsOnceAndReleases() {
        FakeRedis redis = new FakeRedis();
        FakeRebuildLock lock = new FakeRebuildLock();
        CacheService cacheService = new CacheService(redis, PROPERTIES, lock);
        AtomicInteger loads = new AtomicInteger();

        String value = cacheService.loadWithSingleFlight(KEY,
                () -> read(cacheService),
                () -> {
                    loads.incrementAndGet();
                    cacheService.put(KEY, "article", 60, 0);
                    return "article";
                });

        assertEquals("article", value);
        assertEquals(1, loads.get());
        assertEquals(1, lock.acquireCalls.get());
        assertEquals(1, lock.releaseCalls.get());
    }

    @Test
    void lockContentionDegradesWithoutLoading() {
        FakeRedis redis = new FakeRedis();
        FakeRebuildLock lock = new FakeRebuildLock();
        lock.alwaysBusy = true;
        CacheService cacheService = new CacheService(redis, PROPERTIES, lock);
        AtomicInteger loads = new AtomicInteger();

        assertThrows(CacheRebuildInProgressException.class,
                () -> cacheService.loadWithSingleFlight(KEY,
                        () -> read(cacheService),
                        () -> {
                            loads.incrementAndGet();
                            return "article";
                        }));

        assertEquals(0, loads.get());
        assertEquals(1, lock.releaseCalls.get());
    }

    @Test
    void twoInstancesShareOneRebuild() throws Exception {
        FakeRedis redis = new FakeRedis();
        FakeRebuildLock lock = new FakeRebuildLock();
        CacheService first = new CacheService(redis, PROPERTIES, lock);
        CacheService second = new CacheService(redis, PROPERTIES, lock);
        AtomicInteger loads = new AtomicInteger();
        CountDownLatch loaderEntered = new CountDownLatch(1);
        CountDownLatch releaseLoader = new CountDownLatch(1);
        Supplier<String> loader = () -> {
            loads.incrementAndGet();
            loaderEntered.countDown();
            await(releaseLoader);
            first.put(KEY, "article", 60, 0);
            return "article";
        };

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<String> firstResult = pool.submit(
                    () -> first.loadWithSingleFlight(KEY, () -> read(first), loader));
            assertTrue(loaderEntered.await(1, TimeUnit.SECONDS), "loader 未进入");
            Future<String> secondResult = pool.submit(
                    () -> second.loadWithSingleFlight(KEY, () -> read(second), loader));
            Thread.sleep(50);
            releaseLoader.countDown();

            assertEquals("article", firstResult.get(2, TimeUnit.SECONDS));
            assertEquals("article", secondResult.get(2, TimeUnit.SECONDS));
            assertEquals(1, loads.get());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void unavailableDistributedLockFallsBackToLocal() {
        FakeRedis redis = new FakeRedis();
        FakeRebuildLock lock = new FakeRebuildLock();
        lock.unavailable = true;
        CacheService cacheService = new CacheService(redis, PROPERTIES, lock);
        AtomicInteger loads = new AtomicInteger();

        String value = cacheService.loadWithSingleFlight(KEY,
                () -> read(cacheService),
                () -> {
                    loads.incrementAndGet();
                    cacheService.put(KEY, "article", 60, 0);
                    return "article";
                });

        assertEquals("article", value);
        assertEquals(1, loads.get());
        assertEquals(1, lock.acquireCalls.get());
    }

    @Test
    void cachedValueFilledWhileWaitingIsReturned() {
        FakeRedis redis = new FakeRedis();
        FakeRebuildLock lock = new FakeRebuildLock();
        lock.alwaysBusy = true;
        CacheService cacheService = new CacheService(redis, PROPERTIES, lock);
        cacheService.put(KEY, "cached", 60, 0);
        AtomicInteger loads = new AtomicInteger();

        String value = cacheService.loadWithSingleFlight(KEY,
                () -> read(cacheService),
                () -> {
                    loads.incrementAndGet();
                    return "loaded";
                });

        assertEquals("cached", value);
        assertEquals(0, loads.get());
    }

    @Test
    void loaderFailureStillReleasesLock() {
        FakeRedis redis = new FakeRedis();
        FakeRebuildLock lock = new FakeRebuildLock();
        CacheService cacheService = new CacheService(redis, PROPERTIES, lock);

        assertThrows(IllegalStateException.class,
                () -> cacheService.loadWithSingleFlight(KEY,
                        () -> read(cacheService),
                        () -> {
                            throw new IllegalStateException("db down");
                        }));

        assertEquals(1, lock.releaseCalls.get());

        AtomicInteger loads = new AtomicInteger();
        String value = cacheService.loadWithSingleFlight(KEY,
                () -> read(cacheService),
                () -> {
                    loads.incrementAndGet();
                    return "article";
                });

        assertEquals("article", value);
        assertEquals(1, loads.get());
    }

    @Test
    void nonRedisExceptionFromLockPropagates() {
        FakeRedis redis = new FakeRedis();
        FakeRebuildLock lock = new FakeRebuildLock();
        lock.failure = new IllegalStateException("programming bug");
        CacheService cacheService = new CacheService(redis, PROPERTIES, lock);
        AtomicInteger loads = new AtomicInteger();

        assertThrows(IllegalStateException.class,
                () -> cacheService.loadWithSingleFlight(KEY,
                        () -> read(cacheService),
                        () -> {
                            loads.incrementAndGet();
                            return "article";
                        }));

        assertEquals(0, loads.get());
    }

    @Test
    void releaseIsIdempotent() throws Exception {
        FakeRebuildLock lock = new FakeRebuildLock();

        CacheRebuildLockHandle handle = lock.tryAcquire(KEY, 10);
        handle.release();
        handle.release();

        assertEquals(1, lock.releaseCalls.get());
    }

    private static String read(CacheService cacheService) {
        return cacheService.get(KEY, String.class);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(2, TimeUnit.SECONDS)) {
                throw new IllegalStateException("latch timeout");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    /** 共享一把假分布式锁；两个 CacheService 实例用它模拟两台机器。 */
    private static final class FakeRebuildLock implements CacheRebuildLock {

        private final Map<String, ReentrantLock> locks = new ConcurrentHashMap<>();
        private final AtomicInteger acquireCalls = new AtomicInteger();
        private final AtomicInteger releaseCalls = new AtomicInteger();
        private volatile boolean unavailable;
        private volatile boolean alwaysBusy;
        private volatile RuntimeException failure;

        @Override
        public CacheRebuildLockHandle tryAcquire(String cacheKey, long waitMillis)
                throws InterruptedException {
            acquireCalls.incrementAndGet();
            if (failure != null) {
                throw failure;
            }
            if (unavailable) {
                return null;
            }
            ReentrantLock lock = locks.computeIfAbsent(cacheKey, ignored -> new ReentrantLock());
            boolean acquired = !alwaysBusy && lock.tryLock(waitMillis, TimeUnit.MILLISECONDS);
            AtomicBoolean released = new AtomicBoolean();
            return new CacheRebuildLockHandle() {
                @Override
                public boolean isAcquired() {
                    return acquired;
                }

                @Override
                public void release() {
                    if (released.compareAndSet(false, true)) {
                        if (acquired) {
                            lock.unlock();
                        }
                        releaseCalls.incrementAndGet();
                    }
                }
            };
        }
    }

    /** 只实现 get / set / hasKey / delete 的内存 Redis。 */
    private static final class FakeRedis extends StringRedisTemplate {

        private final Map<String, String> store = new ConcurrentHashMap<>();

        @Override
        @SuppressWarnings("unchecked")
        public ValueOperations<String, String> opsForValue() {
            return (ValueOperations<String, String>) Proxy.newProxyInstance(
                    ValueOperations.class.getClassLoader(),
                    new Class<?>[]{ValueOperations.class},
                    (proxy, method, args) -> {
                        switch (method.getName()) {
                            case "get":
                                return store.get((String) args[0]);
                            case "set":
                                store.put((String) args[0], (String) args[1]);
                                return null;
                            default:
                                throw new UnsupportedOperationException(
                                        "unsupported operation: " + method.getName());
                        }
                    });
        }

        @Override
        public Boolean hasKey(String key) {
            return store.containsKey(key);
        }

        @Override
        public Boolean delete(String key) {
            store.remove(key);
            return Boolean.TRUE;
        }
    }
}
