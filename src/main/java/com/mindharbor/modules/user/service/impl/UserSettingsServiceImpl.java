package com.mindharbor.modules.user.service.impl;

import com.mindharbor.modules.user.dto.UserSettingsRequest;
import com.mindharbor.modules.user.entity.UserSettings;
import com.mindharbor.modules.user.mapper.UserSettingsMapper;
import com.mindharbor.modules.user.service.UserSettingsService;
import com.mindharbor.modules.user.vo.UserSettingsVO;
import com.mindharbor.security.CurrentUserProvider;
import java.time.Instant;
import org.springframework.stereotype.Service;

/** 个人偏好设置业务实现：未初始化时返回默认值，不写库。 */
@Service
public class UserSettingsServiceImpl implements UserSettingsService {

    private final UserSettingsMapper userSettingsMapper;
    private final CurrentUserProvider currentUserProvider;

    public UserSettingsServiceImpl(
            UserSettingsMapper userSettingsMapper,
            CurrentUserProvider currentUserProvider) {
        this.userSettingsMapper = userSettingsMapper;
        this.currentUserProvider = currentUserProvider;
    }

    @Override
    public UserSettingsVO current() {
        return toView(findOrDefault(currentUserProvider.getUserId()));
    }

    @Override
    public UserSettingsVO update(UserSettingsRequest request) {
        Long userId = currentUserProvider.getUserId();
        UserSettings settings = findOrDefault(userId);
        applyRequest(settings, request);
        settings.setUpdatedAt(Instant.now());
        if (settings.getId() == null) {
            userSettingsMapper.insert(settings);
        } else {
            userSettingsMapper.updateById(settings);
        }
        return toView(settings);
    }

    @Override
    public void disableDataConsent(Long userId) {
        if (userId == null) {
            return;
        }
        userSettingsMapper.findByUserId(userId).ifPresent(settings -> {
            if (!settings.isDataConsent()) {
                return;
            }
            settings.setDataConsent(false);
            settings.setUpdatedAt(Instant.now());
            userSettingsMapper.updateById(settings);
        });
    }

    private UserSettings findOrDefault(Long userId) {
        return userSettingsMapper.findByUserId(userId)
                .orElseGet(() -> {
                    UserSettings settings = new UserSettings();
                    settings.setUserId(userId);
                    return settings;
                });
    }

    private void applyRequest(UserSettings settings, UserSettingsRequest request) {
        if (request == null) {
            return;
        }
        if (request.getDefaultChatMode() != null) {
            settings.setDefaultChatMode(request.getDefaultChatMode());
        }
        if (request.getStreamSpeed() != null) {
            settings.setStreamSpeed(request.getStreamSpeed());
        }
        if (request.getMessageRetention() != null) {
            settings.setMessageRetention(request.getMessageRetention());
        }
        if (request.getDataConsent() != null) {
            settings.setDataConsent(request.getDataConsent());
        }
        if (request.getTheme() != null) {
            settings.setTheme(request.getTheme());
        }
        if (request.getFontScale() != null) {
            settings.setFontScale(request.getFontScale());
        }
        if (request.getMessageSound() != null) {
            settings.setMessageSound(request.getMessageSound());
        }
        if (request.getCrisisTip() != null) {
            settings.setCrisisTip(request.getCrisisTip());
        }
    }

    private UserSettingsVO toView(UserSettings settings) {
        return new UserSettingsVO(
                settings.getDefaultChatMode().name(),
                settings.getStreamSpeed().name(),
                settings.getMessageRetention().name(),
                settings.isDataConsent(),
                settings.getTheme().name(),
                settings.getFontScale().name(),
                settings.isMessageSound(),
                settings.isCrisisTip(),
                settings.getUpdatedAt());
    }
}
