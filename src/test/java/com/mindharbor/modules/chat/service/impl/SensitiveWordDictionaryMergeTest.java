package com.mindharbor.modules.chat.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.core.type.TypeReference;
import com.mindharbor.common.cache.CacheProperties;
import com.mindharbor.common.cache.CacheService;
import com.mindharbor.modules.chat.entity.SensitiveWord;
import com.mindharbor.modules.chat.vo.SensitiveCheckVO;
import com.mindharbor.security.CurrentUserProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 词库与内置兜底词表的合并规则。
 *
 * <p>原来的实现是"库里有任意一条就用库、整体丢掉内置表"，导致运维只录了一条 CARE 词时，
 * 内置的 6 个 HIGH 词（自杀/轻生/自残/不想活/活不下去/结束生命）全部失效，
 * 高危预警一次都不会触发。这里钉住合并语义：<strong>两边都参与匹配，同词取更高等级</strong>。
 */
class SensitiveWordDictionaryMergeTest {

    private List<SensitiveWord> databaseWords = new ArrayList<>();

    private SensitiveWordServiceImpl service;

    @BeforeEach
    void setUp() {
        CacheProperties properties = new CacheProperties(
                new CacheProperties.ArticleEntry(60, 10, 30, 5),
                new CacheProperties.Entry(60, 10),
                new CacheProperties.Entry(60, 10),
                new CacheProperties.RebuildLock(1000));
        service = new SensitiveWordServiceImpl(
                null,   // 回源被下面的假缓存短路，不会用到 mapper
                null,   // inspect 不写审计事件
                new CurrentUserProvider(),
                fakeCache(properties),
                properties);
    }

    @Test
    @DisplayName("库里只有 CARE 词时，内置的 HIGH 词仍要命中")
    void builtInHighStillWorksWhenDatabaseHasOnlyCareWord() {
        databaseWords = List.of(word("自杀", SensitiveWord.Level.CARE));

        assertEquals(SensitiveCheckVO.Level.HIGH, service.inspect("我想轻生").getLevel(),
                "「轻生」只在内置表里，库非空时也必须生效");
    }

    @Test
    @DisplayName("同一个词两边级别不同时取更高级别")
    void conflictingLevelTakesHigher() {
        databaseWords = List.of(word("自杀", SensitiveWord.Level.CARE));

        assertEquals(SensitiveCheckVO.Level.HIGH, service.inspect("我想自杀").getLevel(),
                "库里配成 CARE、内置是 HIGH，安全场景应取 HIGH");
    }

    @Test
    @DisplayName("库里独有的词照常命中，不受合并影响")
    void databaseOnlyWordStillMatches() {
        databaseWords = List.of(word("外星语", SensitiveWord.Level.HIGH));

        assertEquals(SensitiveCheckVO.Level.HIGH, service.inspect("说外星语").getLevel());
    }

    @Test
    @DisplayName("库为空时仍用内置表（原有行为不能退化）")
    void emptyDatabaseUsesBuiltIn() {
        databaseWords = List.of();

        assertEquals(SensitiveCheckVO.Level.HIGH, service.inspect("轻生").getLevel());
    }

    @Test
    @DisplayName("普通 CARE 词不会被内置 HIGH 词误伤")
    void careWordStaysCare() {
        databaseWords = List.of(word("绝望", SensitiveWord.Level.CARE));

        assertEquals(SensitiveCheckVO.Level.CARE, service.inspect("我很绝望").getLevel());
    }

    @Test
    @DisplayName("未命中任何词时结果不是 CARE 也不是 HIGH")
    void noMatchIsNeither() {
        databaseWords = List.of(word("自杀", SensitiveWord.Level.CARE));

        SensitiveCheckVO.Level level = service.inspect("今天天气不错").getLevel();

        assertEquals(SensitiveCheckVO.Level.NONE, level, "实际=" + level);
    }

    private static SensitiveWord word(String text, SensitiveWord.Level level) {
        SensitiveWord word = new SensitiveWord();
        word.setWord(text);
        word.setLevel(level);
        word.setEnabled(true);
        return word;
    }

    /** 绕过 Redis 与单飞：直接返回预设的"库内容"。 */
    private CacheService fakeCache(CacheProperties properties) {
        return new CacheService(null, properties) {
            @Override
            public <T> T get(String key, TypeReference<T> type) {
                return null;
            }

            @Override
            @SuppressWarnings("unchecked")
            public <T> T loadWithSingleFlight(
                    String key, Supplier<T> cachedValueReader, Supplier<T> loader) {
                return (T) databaseWords;
            }
        };
    }
}
