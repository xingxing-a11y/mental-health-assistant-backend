package com.mindharbor.modules.chat.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.core.type.TypeReference;
import com.mindharbor.common.cache.CacheKeys;
import com.mindharbor.common.cache.CacheProperties;
import com.mindharbor.common.cache.CacheService;
import com.mindharbor.modules.chat.entity.SensitiveWord;
import com.mindharbor.modules.chat.mapper.SensitiveWordMapper;
import com.mindharbor.modules.chat.vo.SensitiveCheckVO;
import com.mindharbor.security.CurrentUserProvider;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

/** 敏感词空词库的缓存行为：空结果也要回填，避免每条消息查库。 */
class SensitiveWordEmptyCacheTest {

    private static final CacheProperties PROPERTIES = new CacheProperties(
            new CacheProperties.ArticleEntry(3600, 600, 300, 60),
            new CacheProperties.Entry(3600, 600),
            new CacheProperties.Entry(1200, 300),
            new CacheProperties.RebuildLock(1000));

    @Test
    void emptyDatabaseIsCachedAndSecondInspectDoesNotQueryAgain() {
        FakeCacheService cacheService = new FakeCacheService(PROPERTIES);
        FakeWordMapper wordMapper = new FakeWordMapper();
        SensitiveWordServiceImpl service = new SensitiveWordServiceImpl(
                wordMapper.mapper(), null, new CurrentUserProvider(), cacheService, PROPERTIES);

        assertEquals(SensitiveCheckVO.Level.HIGH, service.inspect("我想轻生").getLevel());
        assertEquals(SensitiveCheckVO.Level.HIGH, service.inspect("我想轻生").getLevel());

        assertEquals(1, wordMapper.findEnabledCalls.get(),
                "空词库第一次回源后应写入缓存，第二次不能再查库");
    }

    @Test
    void invalidatingEmptyCachePicksUpNewWordImmediately() {
        FakeCacheService cacheService = new FakeCacheService(PROPERTIES);
        FakeWordMapper wordMapper = new FakeWordMapper();
        SensitiveWordServiceImpl service = new SensitiveWordServiceImpl(
                wordMapper.mapper(), null, new CurrentUserProvider(), cacheService, PROPERTIES);

        // 先制造空词库缓存
        assertEquals(SensitiveCheckVO.Level.HIGH, service.inspect("我想轻生").getLevel());

        // 模拟管理端新增词后 invalidateCaches() 删除 key
        wordMapper.words = List.of(word("新词", SensitiveWord.Level.HIGH));
        cacheService.delete(CacheKeys.SENSITIVE_WORD_ENABLED);

        assertEquals(SensitiveCheckVO.Level.HIGH, service.inspect("这里有一个新词").getLevel());
        assertEquals(2, wordMapper.findEnabledCalls.get());
    }

    private static SensitiveWord word(String text, SensitiveWord.Level level) {
        SensitiveWord word = new SensitiveWord();
        word.setWord(text);
        word.setLevel(level);
        word.setEnabled(true);
        return word;
    }

    /** 用内存 Map 模拟 CacheService，重点验证“空结果有没有 put”。 */
    private static final class FakeCacheService extends CacheService {

        private final Map<String, Object> store = new ConcurrentHashMap<>();

        private FakeCacheService(CacheProperties properties) {
            super(null, properties, null);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T get(String key, TypeReference<T> type) {
            return (T) store.get(key);
        }

        @Override
        public void put(String key, Object value, int ttlSeconds, int jitterSeconds) {
            store.put(key, value);
        }

        @Override
        public void delete(String key) {
            store.remove(key);
        }

        @Override
        public <T> T loadWithSingleFlight(String key, Supplier<T> cachedValueReader,
                                          Supplier<T> loader) {
            T cached = cachedValueReader.get();
            return cached != null ? cached : loader.get();
        }
    }

    private static final class FakeWordMapper {

        private final AtomicInteger findEnabledCalls = new AtomicInteger();
        private volatile List<SensitiveWord> words = List.of();

        private SensitiveWordMapper mapper() {
            return (SensitiveWordMapper) Proxy.newProxyInstance(
                    SensitiveWordMapper.class.getClassLoader(),
                    new Class<?>[]{SensitiveWordMapper.class},
                    (proxy, method, args) -> {
                        if ("findEnabled".equals(method.getName())) {
                            findEnabledCalls.incrementAndGet();
                            return new ArrayList<>(words);
                        }
                        throw new UnsupportedOperationException(method.getName());
                    });
        }
    }
}
