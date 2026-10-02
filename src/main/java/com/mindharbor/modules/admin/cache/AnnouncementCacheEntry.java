package com.mindharbor.modules.admin.cache;

import com.mindharbor.modules.admin.entity.Announcement;
import java.time.Instant;

/** 公告缓存快照：字段清单固定，实体增删字段不会让已写入的缓存无法反序列化。 */
public record AnnouncementCacheEntry(Long id, String title, String content, Instant createdAt) {
    public static AnnouncementCacheEntry from(Announcement announcement) {
        return new AnnouncementCacheEntry(announcement.getId(), announcement.getTitle(),
                announcement.getContent(), announcement.getCreatedAt());
    }

    public Announcement toEntity() {
        Announcement announcement = new Announcement();
        announcement.setId(id);
        announcement.setTitle(title);
        announcement.setContent(content);
        announcement.setCreatedAt(createdAt);
        return announcement;
    }
}
