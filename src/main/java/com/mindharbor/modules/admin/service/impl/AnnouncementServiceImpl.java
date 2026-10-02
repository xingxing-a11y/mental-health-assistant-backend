package com.mindharbor.modules.admin.service.impl;

import com.mindharbor.common.ResourceNotFoundException;
import com.mindharbor.common.cache.CacheKeys;
import com.mindharbor.common.cache.CacheProperties;
import com.mindharbor.common.cache.CacheService;
import com.mindharbor.modules.admin.entity.AdminOperationLog;
import com.mindharbor.modules.admin.dto.AnnouncementRequest;
import com.mindharbor.modules.admin.entity.Announcement;
import com.mindharbor.modules.admin.mapper.AnnouncementMapper;
import com.mindharbor.modules.admin.service.AdminOperationLogService;
import com.mindharbor.modules.admin.service.AnnouncementService;
import java.util.List;
import com.fasterxml.jackson.core.type.TypeReference;
import com.mindharbor.modules.admin.cache.AnnouncementCacheEntry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 系统公告业务实现，负责公告的读写与存在性校验。 */
@Service
public class AnnouncementServiceImpl implements AnnouncementService {

    private static final TypeReference<List<AnnouncementCacheEntry>> LIST_TYPE = new TypeReference<>() { };

    private final AnnouncementMapper announcementMapper;

    private final AdminOperationLogService adminOperationLogService;
    private final CacheService cacheService;
    private final CacheProperties cacheProperties;

    public AnnouncementServiceImpl(
            AnnouncementMapper announcementMapper,
            AdminOperationLogService adminOperationLogService,
            CacheService cacheService,
            CacheProperties cacheProperties) {
        this.announcementMapper = announcementMapper;
        this.adminOperationLogService = adminOperationLogService;
        this.cacheService = cacheService;
        this.cacheProperties = cacheProperties;
    }

    @Override
    public List<Announcement> list() {
        List<Announcement> cached = readListCache();
        if (cached != null) {
            return cached;
        }
        return cacheService.loadWithSingleFlight(CacheKeys.ANNOUNCEMENT_LIST, this::readListCache, () -> {
            List<Announcement> announcements = announcementMapper.findAllByOrderByCreatedAtDesc();
            cacheService.put(CacheKeys.ANNOUNCEMENT_LIST,
                    announcements.stream().map(AnnouncementCacheEntry::from).toList(),
                    cacheProperties.announcement().ttlSeconds(),
                    cacheProperties.announcement().jitterSeconds());
            return announcements;
        });
    }

    /** 读取列表缓存：未命中或缓存的 JSON 已损坏时返回 null，交给单飞回源决定是否查库。 */
    private List<Announcement> readListCache() {
        List<AnnouncementCacheEntry> cached = cacheService.get(CacheKeys.ANNOUNCEMENT_LIST, LIST_TYPE);
        return cached == null ? null : cached.stream().map(AnnouncementCacheEntry::toEntity).toList();
    }

    @Override
    @Transactional
    public Announcement create(AnnouncementRequest request) {
        Announcement announcement = new Announcement();
        announcement.setTitle(request.getTitle());
        announcement.setContent(request.getContent());
        announcementMapper.insert(announcement);
        cacheService.deleteAfterCommit(CacheKeys.ANNOUNCEMENT_LIST);
        adminOperationLogService.record(
                AdminOperationLog.Type.ANNOUNCEMENT,
                "announcement",
                announcement.getId(),
                "新增公告");
        return announcement;
    }

    @Override
    @Transactional
    public Announcement update(Long announcementId, AnnouncementRequest request) {
        Announcement announcement = announcementMapper.findById(announcementId)
                .orElseThrow(() -> new ResourceNotFoundException("公告不存在"));
        announcement.setTitle(request.getTitle());
        announcement.setContent(request.getContent());
        announcementMapper.updateById(announcement);
        cacheService.deleteAfterCommit(CacheKeys.ANNOUNCEMENT_LIST);
        adminOperationLogService.record(
                AdminOperationLog.Type.ANNOUNCEMENT,
                "announcement",
                announcement.getId(),
                "编辑公告");
        return announcement;
    }

    @Override
    @Transactional
    public void delete(Long announcementId) {
        announcementMapper.deleteById(announcementId);
        cacheService.deleteAfterCommit(CacheKeys.ANNOUNCEMENT_LIST);
        adminOperationLogService.record(
                AdminOperationLog.Type.ANNOUNCEMENT,
                "announcement",
                announcementId,
                "删除公告");
    }
}
