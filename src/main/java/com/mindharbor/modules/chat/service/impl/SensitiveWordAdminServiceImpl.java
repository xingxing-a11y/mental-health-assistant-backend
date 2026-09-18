package com.mindharbor.modules.chat.service.impl;

import com.mindharbor.common.ResourceNotFoundException;
import com.mindharbor.modules.chat.dto.SensitiveWordRequest;
import com.mindharbor.modules.chat.entity.SensitiveWord;
import com.mindharbor.modules.chat.mapper.SensitiveWordMapper;
import com.mindharbor.modules.chat.service.SensitiveWordAdminService;
import com.mindharbor.modules.chat.vo.SensitiveWordVO;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;

/** 敏感词词库维护实现，重复词条与非法级别在 service 层拦截。 */
@Service
public class SensitiveWordAdminServiceImpl implements SensitiveWordAdminService {

    private final SensitiveWordMapper mapper;

    public SensitiveWordAdminServiceImpl(SensitiveWordMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<SensitiveWordVO> list() {
        return mapper.findAll()
                .stream()
                .map(this::toView)
                .toList();
    }

    @Override
    public SensitiveWordVO create(SensitiveWordRequest request) {
        String word = normalizeWord(request.getWord());
        mapper.findByWord(word).ifPresent(existing -> {
            throw new IllegalArgumentException("该敏感词已存在");
        });

        SensitiveWord entity = new SensitiveWord();
        entity.setWord(word);
        entity.setLevel(request.getLevel());
        entity.setEnabled(request.getEnabled() == null || request.getEnabled());
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());
        mapper.insert(entity);
        return toView(entity);
    }

    @Override
    public SensitiveWordVO update(long wordId, SensitiveWordRequest request) {
        SensitiveWord entity = mapper.selectById(wordId);
        if (entity == null) {
            throw new ResourceNotFoundException("敏感词不存在");
        }

        String word = normalizeWord(request.getWord());
        mapper.findByWord(word)
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
        mapper.updateById(entity);
        return toView(entity);
    }

    @Override
    public void delete(long wordId) {
        if (mapper.selectById(wordId) == null) {
            throw new ResourceNotFoundException("敏感词不存在");
        }
        mapper.deleteById(wordId);
    }

    private String normalizeWord(String word) {

        return word.trim();

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
