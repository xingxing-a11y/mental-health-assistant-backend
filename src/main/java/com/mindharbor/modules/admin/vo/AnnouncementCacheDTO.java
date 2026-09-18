package com.mindharbor.modules.admin.vo;

import com.mindharbor.modules.admin.entity.Announcement;
import java.time.Instant;

public record AnnouncementCacheDTO(Long id, String title, String content, Instant createdAt) {
    public static AnnouncementCacheDTO from(Announcement announcement) {
        return new AnnouncementCacheDTO(announcement.getId(), announcement.getTitle(),
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
