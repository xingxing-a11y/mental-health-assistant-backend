package com.mindharbor.modules.admin.service.impl;

import com.mindharbor.common.ResourceNotFoundException;
import com.mindharbor.modules.admin.entity.AdminOperationLog;
import com.mindharbor.modules.admin.dto.AnnouncementRequest;
import com.mindharbor.modules.admin.entity.Announcement;
import com.mindharbor.modules.admin.mapper.AnnouncementMapper;
import com.mindharbor.modules.admin.service.AdminOperationLogService;
import com.mindharbor.modules.admin.service.AnnouncementService;
import java.util.List;
import org.springframework.stereotype.Service;

/** 系统公告业务实现，负责公告的读写与存在性校验。 */
@Service
public class AnnouncementServiceImpl implements AnnouncementService {

    private final AnnouncementMapper announcementMapper;

    private final AdminOperationLogService operationLogService;

    public AnnouncementServiceImpl(
            AnnouncementMapper announcementMapper,
            AdminOperationLogService operationLogService) {
        this.announcementMapper = announcementMapper;
        this.operationLogService = operationLogService;
    }

    @Override
    public List<Announcement> list() {
        return announcementMapper.findAllByOrderByCreatedAtDesc();
    }

    @Override
    public Announcement create(AnnouncementRequest request) {
        Announcement announcement = new Announcement();
        announcement.setTitle(request.getTitle());
        announcement.setContent(request.getContent());
        announcementMapper.insert(announcement);
        operationLogService.record(
                AdminOperationLog.Type.ANNOUNCEMENT,
                "announcement",
                announcement.getId(),
                "新增公告");
        return announcement;
    }

    @Override
    public Announcement update(Long announcementId, AnnouncementRequest request) {
        Announcement announcement = announcementMapper.findById(announcementId)
                .orElseThrow(() -> new ResourceNotFoundException("公告不存在"));
        announcement.setTitle(request.getTitle());
        announcement.setContent(request.getContent());
        announcementMapper.updateById(announcement);
        operationLogService.record(
                AdminOperationLog.Type.ANNOUNCEMENT,
                "announcement",
                announcement.getId(),
                "编辑公告");
        return announcement;
    }

    @Override
    public void delete(Long announcementId) {
        announcementMapper.deleteById(announcementId);
        operationLogService.record(
                AdminOperationLog.Type.ANNOUNCEMENT,
                "announcement",
                announcementId,
                "删除公告");
    }
}
