package com.mindharbor.modules.chat.vo;

import com.mindharbor.modules.chat.entity.SensitiveWord;
import java.time.Instant;

public record SensitiveWordCacheDTO(Long id, String word, SensitiveWord.Level level,
                                    boolean enabled, Instant createdAt, Instant updatedAt) {
    public static SensitiveWordCacheDTO from(SensitiveWord word) {
        return new SensitiveWordCacheDTO(word.getId(), word.getWord(), word.getLevel(),
                word.isEnabled(), word.getCreatedAt(), word.getUpdatedAt());
    }

    public SensitiveWord toEntity() {
        SensitiveWord wordEntity = new SensitiveWord();
        wordEntity.setId(id);
        wordEntity.setWord(word);
        wordEntity.setLevel(level);
        wordEntity.setEnabled(enabled);
        wordEntity.setCreatedAt(createdAt);
        wordEntity.setUpdatedAt(updatedAt);
        return wordEntity;
    }
}
