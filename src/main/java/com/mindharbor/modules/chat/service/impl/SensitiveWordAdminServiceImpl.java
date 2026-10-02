package com.mindharbor.modules.chat.service.impl;

import com.mindharbor.common.ResourceNotFoundException;
import com.mindharbor.common.cache.CacheKeys;
import com.mindharbor.common.cache.CacheService;
import com.mindharbor.modules.admin.entity.AdminOperationLog;
import com.mindharbor.modules.admin.service.AdminOperationLogService;
import com.mindharbor.modules.chat.dto.SensitiveWordRequest;
import com.mindharbor.modules.chat.entity.SensitiveWord;
import com.mindharbor.modules.chat.mapper.SensitiveWordMapper;
import com.mindharbor.modules.chat.service.SensitiveWordAdminService;
import com.mindharbor.modules.chat.vo.SensitiveWordVO;
import java.time.Instant;
import java.util.List;
import com.fasterxml.jackson.core.type.TypeReference;
import com.mindharbor.common.cache.CacheProperties;
import com.mindharbor.modules.chat.cache.SensitiveWordCacheEntry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 敏感词词库维护实现，重复词条与非法级别在 service 层拦截。 */
@Service
public class SensitiveWordAdminServiceImpl implements SensitiveWordAdminService {

    private static final TypeReference<List<SensitiveWordCacheEntry>> ALL_CACHE_TYPE =
            new TypeReference<>() { };

    private final SensitiveWordMapper sensitiveWordMapper;

    private final AdminOperationLogService adminOperationLogService;
    private final CacheService cacheService;
    private final CacheProperties cacheProperties;

    public SensitiveWordAdminServiceImpl(
            SensitiveWordMapper sensitiveWordMapper,
            AdminOperationLogService adminOperationLogService,
            CacheService cacheService,
            CacheProperties cacheProperties) {
        this.sensitiveWordMapper = sensitiveWordMapper;
        this.adminOperationLogService = adminOperationLogService;
        this.cacheService = cacheService;
        this.cacheProperties = cacheProperties;
    }

    @Override
    public List<SensitiveWordVO> list() {
        List<SensitiveWord> cached = readAllCache();
        List<SensitiveWord> words = cached != null
                ? cached
                : cacheService.loadWithSingleFlight(
                        CacheKeys.SENSITIVE_WORD_ALL,
                        this::readAllCache,
                        this::loadAllWordsFromDatabase);
        return words.stream().map(this::toView).toList();
    }

    /** 读取完整词库缓存：未命中或缓存的 JSON 已损坏时返回 null，交给单飞回源决定是否查库。 */
    private List<SensitiveWord> readAllCache() {
        List<SensitiveWordCacheEntry> cached =
                cacheService.get(CacheKeys.SENSITIVE_WORD_ALL, ALL_CACHE_TYPE);
        return cached == null ? null : cached.stream().map(SensitiveWordCacheEntry::toEntity).toList();
    }

    /** 回源完整词库并回填缓存。 */
    private List<SensitiveWord> loadAllWordsFromDatabase() {
        List<SensitiveWord> loaded = sensitiveWordMapper.findAll();
        cacheService.put(CacheKeys.SENSITIVE_WORD_ALL,
                loaded.stream().map(SensitiveWordCacheEntry::from).toList(),
                cacheProperties.sensitiveWord().ttlSeconds(),
                cacheProperties.sensitiveWord().jitterSeconds());
        return loaded;
    }

    @Override
    @Transactional
    public SensitiveWordVO create(SensitiveWordRequest request) {
        String word = normalizeWord(request.getWord());
        sensitiveWordMapper.findByWord(word).ifPresent(existing -> {
            throw new IllegalArgumentException("该敏感词已存在");
        });

        SensitiveWord entity = new SensitiveWord();
        entity.setWord(word);
        entity.setLevel(request.getLevel());
        entity.setEnabled(request.getEnabled() == null || request.getEnabled());
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());
        sensitiveWordMapper.insert(entity);
        invalidateCaches();
        adminOperationLogService.record(
                AdminOperationLog.Type.SENSITIVE_WORD,
                "sensitive_word",
                entity.getId(),
                "新增敏感词规则");
        return toView(entity);
    }

    @Override
    @Transactional
    public SensitiveWordVO update(long wordId, SensitiveWordRequest request) {
        SensitiveWord entity = sensitiveWordMapper.selectById(wordId);
        if (entity == null) {
            throw new ResourceNotFoundException("敏感词不存在");
        }

        String word = normalizeWord(request.getWord());
        sensitiveWordMapper.findByWord(word)
                .filter(existing -> !existing.getId().equals(wordId))
                .ifPresent(existing -> {
                    throw new IllegalArgumentException("该敏感词已存在");
                });

        entity.setWord(word);
        entity.setLevel(request.getLevel());
        if (request.getEnabled() != null) {
            entity.setEnabled(request.getEnabled());
        }
        entity.setUpdatedAt(Instant.now());
        sensitiveWordMapper.updateById(entity);
        invalidateCaches();
        adminOperationLogService.record(
                AdminOperationLog.Type.SENSITIVE_WORD,
                "sensitive_word",
                entity.getId(),
                "修改敏感词规则");
        return toView(entity);
    }

    @Override
    @Transactional
    public void delete(long wordId) {
        if (sensitiveWordMapper.selectById(wordId) == null) {
            throw new ResourceNotFoundException("敏感词不存在");
        }
        sensitiveWordMapper.deleteById(wordId);
        invalidateCaches();
        adminOperationLogService.record(
                AdminOperationLog.Type.SENSITIVE_WORD,
                "sensitive_word",
                wordId,
                "删除敏感词规则");
    }

    private String normalizeWord(String word) {

        return word.trim();

    }

    private void invalidateCaches() {
        cacheService.deleteAfterCommit(
                CacheKeys.SENSITIVE_WORD_ENABLED,
                CacheKeys.SENSITIVE_WORD_ALL);
    }

    private SensitiveWordVO toView(SensitiveWord entity) {
        return new SensitiveWordVO(
                entity.getId(),
                entity.getWord(),
                entity.getLevel() == null ? null : entity.getLevel().name(),
                entity.isEnabled(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
