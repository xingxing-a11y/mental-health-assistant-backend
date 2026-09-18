package com.mindharbor.modules.user.dto;

import com.mindharbor.modules.user.entity.UserSettings.ChatMode;
import com.mindharbor.modules.user.entity.UserSettings.FontScale;
import com.mindharbor.modules.user.entity.UserSettings.MessageRetention;
import com.mindharbor.modules.user.entity.UserSettings.StreamSpeed;
import com.mindharbor.modules.user.entity.UserSettings.Theme;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 偏好设置更新请求，字段为空表示不修改该项。 */
@Getter
@Setter
@NoArgsConstructor
public class UserSettingsRequest {

    private ChatMode defaultChatMode;

    private StreamSpeed streamSpeed;

    private MessageRetention messageRetention;

    private Boolean dataConsent;

    private Theme theme;

    private FontScale fontScale;

    private Boolean messageSound;

    private Boolean crisisTip;
}
