package com.mindharbor.modules.chat.service.impl;

import com.mindharbor.modules.chat.entity.SensitiveEvent;
import com.mindharbor.modules.chat.entity.SensitiveWord;
import com.mindharbor.common.cache.CacheKeys;
import com.mindharbor.common.cache.CacheProperties;
import com.mindharbor.common.cache.CacheRebuildInProgressException;
import com.mindharbor.common.cache.CacheService;
import com.mindharbor.modules.chat.mapper.SensitiveEventMapper;
import com.mindharbor.modules.chat.mapper.SensitiveWordMapper;
import com.mindharbor.modules.chat.service.SensitiveWordService;
import com.mindharbor.modules.chat.vo.SensitiveCheckVO;
import com.mindharbor.security.CurrentUserProvider;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.core.type.TypeReference;
import com.mindharbor.modules.chat.cache.SensitiveWordCacheEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** 敏感词判定实现：词库优先，命中级别取最高，提示文案统一且不回显原词。 */
@Service
public class SensitiveWordServiceImpl implements SensitiveWordService {

    private static final Logger log = LoggerFactory.getLogger(SensitiveWordServiceImpl.class);

    private static final String CARE_PROMPT =
            "听起来你现在很不容易。如果愿意，可以多说说发生了什么；"
                    + "如果这种感受持续存在，建议联系信任的人或寻求专业心理支持。";

    private static final String HIGH_PROMPT =
            "你提到的内容让我们很担心你。如果你正处于立即危险中，"
                    + "请马上联系家人、拨打心理援助热线或前往医院急诊。";

    private static final String HOTLINE = "全国心理援助热线 12356，紧急情况请拨打 120";

    private static final TypeReference<List<SensitiveWordCacheEntry>> ENABLED_CACHE_TYPE =
            new TypeReference<>() { };

    /** 词库为空或不可用时的兜底词表，保证服务端校验不因数据问题失效。 */
    private static final List<SensitiveWord> BUILT_IN_WORDS = buildBuiltInWords();

    private final SensitiveWordMapper sensitiveWordMapper;

    private final SensitiveEventMapper sensitiveEventMapper;

    private final CurrentUserProvider currentUserProvider;
    private final CacheService cacheService;
    private final CacheProperties cacheProperties;

    public SensitiveWordServiceImpl(
            SensitiveWordMapper sensitiveWordMapper,
            SensitiveEventMapper sensitiveEventMapper,
            CurrentUserProvider currentUserProvider,
            CacheService cacheService,
            CacheProperties cacheProperties) {
        this.sensitiveWordMapper = sensitiveWordMapper;
        this.sensitiveEventMapper = sensitiveEventMapper;
        this.currentUserProvider = currentUserProvider;
        this.cacheService = cacheService;
        this.cacheProperties = cacheProperties;
    }

    @Override
    public SensitiveCheckVO inspect(String content) {
        return toView(match(content));
    }

    @Override
    public SensitiveCheckVO inspectAndAudit(String content, Long sessionId) {
        SensitiveWord matched = match(content);
        if (matched != null && matched.getLevel() == SensitiveWord.Level.CARE) {
            recordEvent(matched, sessionId);
        }
        return toView(matched);
    }

    private SensitiveCheckVO toView(SensitiveWord matched) {
        if (matched == null) {
            return new SensitiveCheckVO(false, SensitiveCheckVO.Level.NONE, null, null);
        }
        if (matched.getLevel() == SensitiveWord.Level.HIGH) {
            return new SensitiveCheckVO(true, SensitiveCheckVO.Level.HIGH, HIGH_PROMPT, HOTLINE);
        }
        return new SensitiveCheckVO(true, SensitiveCheckVO.Level.CARE, CARE_PROMPT, HOTLINE);
    }

    /** 返回命中级别最高的词条；同等级别只保留最先命中（词库排序靠前）的一条。 */
    private SensitiveWord match(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }

        String normalized = content.toLowerCase();
        SensitiveWord careMatch = null;
        for (SensitiveWord word : loadWords()) {
            String keyword = word.getWord();
            if (keyword == null || keyword.isBlank()) {
                continue;
            }
            if (!normalized.contains(keyword.trim().toLowerCase())) {
                continue;
            }
            if (word.getLevel() == SensitiveWord.Level.HIGH) {
                return word;
            }
            if (careMatch == null) {
                careMatch = word;
            }
        }
        return careMatch;
    }

    /** 词库读取失败时退回内置词表，避免安全校验被数据问题绕过。 */
    private List<SensitiveWord> loadWords() {
        try {
            List<SensitiveWord> cached = readEnabledCache();
            if (cached != null) {
                return mergeWithBuiltIn(cached);
            }
            List<SensitiveWord> words = cacheService.loadWithSingleFlight(
                    CacheKeys.SENSITIVE_WORD_ENABLED,
                    this::readEnabledCache,
                    this::loadEnabledWordsFromDatabase);
            if (words != null && !words.isEmpty()) {
                return mergeWithBuiltIn(words);
            }
        } catch (CacheRebuildInProgressException exception) {
            // 词库正在被其他实例重建：预期内降级，用内置词表兜底，不记为故障
            log.warn("Sensitive word cache is rebuilding, fallback to built-in words");
        } catch (RuntimeException exception) {
            // 词库异常信息里可能带上敏感词原文，日志只记录异常类型，完整堆栈放到 DEBUG。
            log.error("Sensitive word dictionary unavailable, fallback to built-in words, error={}",
                    exception.getClass().getSimpleName());
            log.debug("Sensitive word dictionary failure detail", exception);
        }
        return BUILT_IN_WORDS;
    }

    /**
     * 库里的词与内置兜底表合并：两边都参与匹配，同一个词取更高等级。
     *
     * <p>不能"库非空就整体丢掉内置表"——运维只录了一条 CARE 词时，内置的 6 个 HIGH 词
     * 会全部失效，高危预警一次都不会触发。安全场景漏报的代价远大于误报。
     */
    private static List<SensitiveWord> mergeWithBuiltIn(List<SensitiveWord> loaded) {
        Map<String, SensitiveWord> byWord = new LinkedHashMap<>();
        for (SensitiveWord word : BUILT_IN_WORDS) {
            byWord.put(word.getWord(), word);
        }
        for (SensitiveWord word : loaded) {
            if (word == null || word.getWord() == null || word.getWord().isBlank()) {
                continue;
            }
            SensitiveWord existing = byWord.get(word.getWord());
            // 库里新增的词直接补进来；同词只有在库里级别更高时才覆盖内置。
            if (existing == null || word.getLevel() == SensitiveWord.Level.HIGH) {
                byWord.put(word.getWord(), word);
            }
        }
        return new ArrayList<>(byWord.values());
    }

    /** 读取启用词库缓存：未命中或缓存的 JSON 已损坏时返回 null，交给单飞回源决定是否查库。 */
    private List<SensitiveWord> readEnabledCache() {
        List<SensitiveWordCacheEntry> cached =
                cacheService.get(CacheKeys.SENSITIVE_WORD_ENABLED, ENABLED_CACHE_TYPE);
        return cached == null ? null : cached.stream().map(SensitiveWordCacheEntry::toEntity).toList();
    }

    /** 回源启用词库：空词库不回填缓存，避免把"暂时没有词条"固化成长期结果。 */
    private List<SensitiveWord> loadEnabledWordsFromDatabase() {
        List<SensitiveWord> loaded = sensitiveWordMapper.findEnabled();
        if (loaded != null && !loaded.isEmpty()) {
            cacheService.put(CacheKeys.SENSITIVE_WORD_ENABLED,
                    loaded.stream().map(SensitiveWordCacheEntry::from).toList(),
                    cacheProperties.sensitiveWord().ttlSeconds(),
                    cacheProperties.sensitiveWord().jitterSeconds());
        }
        return loaded;
    }

    /** 审计只落词条编号与级别，绝不写入用户原文。 */
    private void recordEvent(SensitiveWord matched, Long sessionId) {
        try {
            SensitiveEvent event = new SensitiveEvent();
            event.setUserId(currentUserProvider.getUserId());
            event.setSessionId(sessionId);
            event.setWordId(matched.getId());
            event.setLevel(matched.getLevel());
            sensitiveEventMapper.insert(event);
        } catch (RuntimeException exception) {
            log.error("Failed to record sensitive event", exception);
        }
    }

    private static List<SensitiveWord> buildBuiltInWords() {
        List<SensitiveWord> words = new ArrayList<>();
        words.add(builtIn("自杀", SensitiveWord.Level.HIGH));
        words.add(builtIn("轻生", SensitiveWord.Level.HIGH));
        words.add(builtIn("自残", SensitiveWord.Level.HIGH));
        words.add(builtIn("不想活", SensitiveWord.Level.HIGH));
        words.add(builtIn("活不下去", SensitiveWord.Level.HIGH));
        words.add(builtIn("结束生命", SensitiveWord.Level.HIGH));
        words.add(builtIn("绝望", SensitiveWord.Level.CARE));
        words.add(builtIn("崩溃", SensitiveWord.Level.CARE));
        words.add(builtIn("撑不下去", SensitiveWord.Level.CARE));
        words.add(builtIn("没有意义", SensitiveWord.Level.CARE));
        return List.copyOf(words);
    }

    private static SensitiveWord builtIn(String word, SensitiveWord.Level level) {
        SensitiveWord entity = new SensitiveWord();
        entity.setWord(word);
        entity.setLevel(level);
        entity.setEnabled(true);
        return entity;
    }
}
