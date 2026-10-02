package com.mindharbor.modules.notification.service;

import com.mindharbor.common.PageResult;
import com.mindharbor.modules.notification.dto.NotificationPushRequest;
import com.mindharbor.modules.notification.vo.NotificationVO;

/** 通知业务接口：用户侧读取个人可见通知，管理员侧负责推送与撤回。 */
public interface NotificationService {

    PageResult<NotificationVO> list(int page, int size);

    /** 未读数单独提供轻量接口，供铃铛低频轮询。 */
    long unreadCount();

    void markRead(long notificationId);

    void markAllRead();

    NotificationVO push(NotificationPushRequest request);

    PageResult<NotificationVO> adminList(int page, int size);

    void revoke(long notificationId);
}
