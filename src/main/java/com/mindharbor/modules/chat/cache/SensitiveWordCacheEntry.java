package com.mindharbor.modules.chat.cache;

import com.mindharbor.modules.chat.entity.SensitiveWord;
import java.time.Instant;

/** 敏感词缓存快照：全量与启用中两套缓存共用，字段清单固定，实体增删字段不会让已写入的缓存无法反序列化。 */
public record SensitiveWordCacheEntry(Long id, String word, SensitiveWord.Level level,
                                      boolean enabled, Instant createdAt, Instant updatedAt) {
    public static SensitiveWordCacheEntry from(SensitiveWord word) {
        return new SensitiveWordCacheEntry(word.getId(), word.getWord(), word.getLevel(),
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
