package com.mindharbor.modules.relax.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 放松练习记录：呼吸放松与正念冥想共用同一张表。 */
@Getter
@Setter
@NoArgsConstructor
@TableName("relax_records")
public class RelaxRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Type type;

    private int durationSeconds;

    private Integer rounds;

    private Instant createdAt = Instant.now();

    public enum Type {
        BREATHE,
        MEDITATION
    }
}
