package com.mindharbor.modules.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 全局系统配置项，键值对形式保存。 */
@Getter
@Setter
@NoArgsConstructor
@TableName("system_settings")
public class SystemSetting {

    @TableId(type = IdType.INPUT)
    private String settingKey;

    private String settingValue;

    private Instant updatedAt = Instant.now();
}
