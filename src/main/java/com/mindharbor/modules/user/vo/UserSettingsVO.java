package com.mindharbor.modules.user.vo;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class UserSettingsVO {

    private String defaultChatMode;

    private String streamSpeed;

    private String messageRetention;

    private boolean dataConsent;

    private String theme;

    private String fontScale;

    private boolean messageSound;

    private boolean crisisTip;

    private Instant updatedAt;
}
