package com.mindharbor.modules.relax.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 压力打卡：只记录等级与备注，不要求写长篇日记。 */
@Getter
@Setter
@NoArgsConstructor
@TableName("stress_logs")
public class StressLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 压力等级 1-10，数值越大压力越高。 */
    private int level;

    private String note;

    private Instant createdAt = Instant.now();
}
