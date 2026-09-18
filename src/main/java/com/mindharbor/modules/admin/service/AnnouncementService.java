package com.mindharbor.modules.admin.service;

import com.mindharbor.modules.admin.dto.AnnouncementRequest;
import com.mindharbor.modules.admin.entity.Announcement;
import java.util.List;

/** 系统公告业务接口，公告的查询与维护统一由该接口对外提供。 */
public interface AnnouncementService {

    List<Announcement> list();

    Announcement create(AnnouncementRequest request);

    Announcement update(Long announcementId, AnnouncementRequest request);

    void delete(Long announcementId);
}
