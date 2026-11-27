package com.mindharbor.common.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

/** 详情型缓存穿透保护的单元测试：不连 Redis、不起 Spring 容器。 */
class CacheServiceMissProtectionTest {

    private static final String DETAIL_KEY = "mindharbor:cache:article:1";
    private static final String MISSING_KEY = "mindharbor:cache:article:missing:1";

    private static final CacheProperties PROPERTIES = new CacheProperties(
            new CacheProperties.ArticleEntry(3600, 600, 300, 60),
            new CacheProperties.Entry(3600, 600),
            new CacheProperties.Entry(1200, 300),
            new CacheProperties.RebuildLock(100));

    private static final CacheService.MissProtection SPEC =
            new CacheService.MissProtection(DETAIL_KEY, MISSING_KEY, 3600, 600, 300, 60);

    @Test
    void cacheHitDoesNotLoad() {
        FakeRedis redis = new FakeRedis();
        redis.store.put(DETAIL_KEY, "\"cached-article\"");
        CacheService cacheService = new CacheService(redis, PROPERTIES, null);
        AtomicInteger loads = new AtomicInteger();

        String value = cacheService.loadDetailWithMissProtection(SPEC,
                () -> readDetail(cacheService),
                cached -> cached,
                () -> {
                    loads.incrementAndGet();
                    return "loaded";
                });

        assertEquals("cached-article", value);
        assertEquals(0, loads.get());
        assertEquals(1, redis.getCount.get());
        assertEquals(0, redis.hasKeyCount.get());
        assertEquals(0, redis.setCount.get());
    }

    @Test
    void missingMarkerReturnsNullWithoutEnteringSingleFlight() {
        FakeRedis redis = new FakeRedis();
        redis.store.put(MISSING_KEY, "true");
        CacheService cacheService = new CacheService(redis, PROPERTIES, null);
        AtomicInteger loads = new AtomicInteger();

        String value = cacheService.loadDetailWithMissProtection(SPEC,
                () -> readDetail(cacheService),
                cached -> cached,
                () -> {
                    loads.incrementAndGet();
                    return "loaded";
                });

        assertNull(value);
        assertEquals(0, loads.get());
        // 只走快速路径的这一次读；进入单飞会变成 2 次
        assertEquals(1, redis.getCount.get());
        assertEquals(1, redis.hasKeyCount.get());
        assertEquals(0, redis.setCount.get());
    }

    @Test
    void firstMissLoadsAndCaches() {
        FakeRedis redis = new FakeRedis();
        CacheService cacheService = new CacheService(redis, PROPERTIES, null);
        AtomicInteger loads = new AtomicInteger();

        String value = cacheService.loadDetailWithMissProtection(SPEC,
                () -> readDetail(cacheService),
                cached -> "cached:" + cached,
                () -> {
                    loads.incrementAndGet();
                    return "article";
                });

        assertEquals("article", value);
        assertEquals(1, loads.get());
        assertEquals(1, redis.setCount.get());
        assertEquals("\"cached:article\"", redis.store.get(DETAIL_KEY));
        assertFalse(redis.store.containsKey(MISSING_KEY));
        long ttl = redis.ttls.get(DETAIL_KEY).getSeconds();
        assertTrue(ttl >= 3600 && ttl <= 4200, "detail ttl=" + ttl);
    }

    @Test
    void missingDataWritesMarker() {
        FakeRedis redis = new FakeRedis();
        CacheService cacheService = new CacheService(redis, PROPERTIES, null);
        AtomicInteger loads = new AtomicInteger();

        String value = cacheService.loadDetailWithMissProtection(SPEC,
                () -> readDetail(cacheService),
                cached -> cached,
                () -> {
                    loads.incrementAndGet();
                    return null;
                });

        assertNull(value);
        assertEquals(1, loads.get());
        assertEquals("true", redis.store.get(MISSING_KEY));
        assertFalse(redis.store.containsKey(DETAIL_KEY));
        long ttl = redis.ttls.get(MISSING_KEY).getSeconds();
        assertTrue(ttl >= 300 && ttl <= 360, "missing ttl=" + ttl);
    }

    @Test
    void concurrentMissLoadsOnce() throws Exception {
        FakeRedis redis = new FakeRedis();
        CacheService cacheService = new CacheService(redis, PROPERTIES, null);
        AtomicInteger loads = new AtomicInteger();
        CountDownLatch loaderEntered = new CountDownLatch(1);
        CountDownLatch releaseLoader = new CountDownLatch(1);
        Supplier<String> loader = () -> {
            loads.incrementAndGet();
            loaderEntered.countDown();
            await(releaseLoader);
            return "article";
        };

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<String> first = pool.submit(() -> cacheService.loadDetailWithMissProtection(
                    SPEC, () -> readDetail(cacheService), cached -> cached, loader));
            assertTrue(loaderEntered.await(1, TimeUnit.SECONDS), "loader 未进入");
            Future<String> second = pool.submit(() -> cacheService.loadDetailWithMissProtection(
                    SPEC, () -> readDetail(cacheService), cached -> cached, loader));
            // 让第二个线程走到等锁的位置，再放行回源
            Thread.sleep(50);
            releaseLoader.countDown();

            assertEquals("article", first.get(2, TimeUnit.SECONDS));
            assertEquals("article", second.get(2, TimeUnit.SECONDS));
            assertEquals(1, loads.get());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void mapperFailureStillReturnsValue() {
        FakeRedis redis = new FakeRedis();
        CacheService cacheService = new CacheService(redis, PROPERTIES, null);

        String value = cacheService.loadDetailWithMissProtection(SPEC,
                () -> readDetail(cacheService),
                cached -> {
                    throw new IllegalStateException("mapping failed");
                },
                () -> "article");

        assertEquals("article", value);
        assertEquals(0, redis.setCount.get());
    }

    @Test
    void loaderFailureDoesNotWriteMissingMarker() {
        FakeRedis redis = new FakeRedis();
        CacheService cacheService = new CacheService(redis, PROPERTIES, null);

        assertThrows(IllegalStateException.class,
                () -> cacheService.loadDetailWithMissProtection(SPEC,
                        () -> readDetail(cacheService),
                        cached -> cached,
                        () -> {
                            throw new IllegalStateException("db down");
                        }));

        assertFalse(redis.store.containsKey(MISSING_KEY));
        assertEquals(0, redis.setCount.get());
    }

    @Test
    void redisFailureFallsBackToLoader() {
        FakeRedis redis = new FakeRedis();
        redis.failReads = true;
        CacheService cacheService = new CacheService(redis, PROPERTIES, null);

        String value = cacheService.loadDetailWithMissProtection(SPEC,
                () -> readDetail(cacheService),
                cached -> cached,
                () -> "article");

        assertEquals("article", value);
        assertEquals(0, redis.setCount.get());
    }

    @Test
    void invalidSpecRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new CacheService.MissProtection("same", "same", 1, 0, 1, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new CacheService.MissProtection(DETAIL_KEY, MISSING_KEY, 0, 0, 1, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new CacheService.MissProtection(DETAIL_KEY, MISSING_KEY, 1, -1, 1, 0));
        assertThrows(NullPointerException.class,
                () -> new CacheService.MissProtection(null, MISSING_KEY, 1, 0, 1, 0));
    }

    private static String readDetail(CacheService cacheService) {
        return cacheService.get(DETAIL_KEY, String.class);
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

    /** 内存版 StringRedisTemplate：只实现 get / set / hasKey / delete，够缓存组件使用。 */
    private static final class FakeRedis extends StringRedisTemplate {

        private final Map<String, String> store = new ConcurrentHashMap<>();
        private final Map<String, Duration> ttls = new ConcurrentHashMap<>();
        private final AtomicInteger getCount = new AtomicInteger();
        private final AtomicInteger setCount = new AtomicInteger();
        private final AtomicInteger hasKeyCount = new AtomicInteger();
        private volatile boolean failReads;

        @Override
        @SuppressWarnings("unchecked")
        public ValueOperations<String, String> opsForValue() {
            return (ValueOperations<String, String>) Proxy.newProxyInstance(
                    ValueOperations.class.getClassLoader(),
                    new Class<?>[]{ValueOperations.class},
                    (proxy, method, args) -> {
                        switch (method.getName()) {
                            case "get":
                                checkReadable();
                                getCount.incrementAndGet();
                                return store.get((String) args[0]);
                            case "set":
                                checkReadable();
                                setCount.incrementAndGet();
                                store.put((String) args[0], (String) args[1]);
                                if (args.length > 2 && args[2] instanceof Duration duration) {
                                    ttls.put((String) args[0], duration);
                                }
                                return null;
                            default:
                                throw new UnsupportedOperationException(
                                        "unsupported operation: " + method.getName());
                        }
                    });
        }

        @Override
        public Boolean hasKey(String key) {
            checkReadable();
            hasKeyCount.incrementAndGet();
            return store.containsKey(key);
        }

        @Override
        public Boolean delete(String key) {
            store.remove(key);
            ttls.remove(key);
            return Boolean.TRUE;
        }

        private void checkReadable() {
            if (failReads) {
                throw new IllegalStateException("redis unavailable");
            }
        }
    }
}
