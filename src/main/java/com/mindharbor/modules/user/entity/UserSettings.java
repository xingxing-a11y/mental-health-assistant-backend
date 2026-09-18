package com.mindharbor.modules.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 个人偏好设置，纯偏好数据，任何判定逻辑都放在 service 层。 */
@Getter
@Setter
@NoArgsConstructor
@TableName("user_settings")
public class UserSettings {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private ChatMode defaultChatMode = ChatMode.COMPANION;

    private StreamSpeed streamSpeed = StreamSpeed.NORMAL;

    private MessageRetention messageRetention = MessageRetention.PERMANENT;

    private boolean dataConsent;

    private Theme theme = Theme.LIGHT;

    private FontScale fontScale = FontScale.MEDIUM;

    private boolean messageSound = true;

    private boolean crisisTip = true;

    private Instant updatedAt = Instant.now();

    public enum ChatMode {
        COMPANION,
        ANALYTICAL
    }

    public enum StreamSpeed {
        SLOW,
        NORMAL,
        FAST
    }

    public enum MessageRetention {
        PERMANENT,
        THIRTY_DAYS
    }

    public enum Theme {
        LIGHT,
        DARK
    }

    public enum FontScale {
        SMALL,
        MEDIUM,
        LARGE
    }
}
