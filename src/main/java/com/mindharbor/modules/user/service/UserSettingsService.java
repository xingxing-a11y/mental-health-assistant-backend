package com.mindharbor.modules.user.service;

import com.mindharbor.modules.user.dto.UserSettingsRequest;
import com.mindharbor.modules.user.vo.UserSettingsVO;

/** 个人偏好设置业务接口。 */
public interface UserSettingsService {

    UserSettingsVO current();

    UserSettingsVO update(UserSettingsRequest request);

    /** 注销账号时强制关闭数据授权，保证注销后不再参与知识库优化。 */
    void disableDataConsent(Long userId);
}
