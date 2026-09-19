package com.mindharbor.modules.diary.vo;

import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 情绪趋势点：date 为日记创建时间，emotion 为情绪枚举名。 */
@Getter
@Setter
@NoArgsConstructor
public class EmotionTrendPointVO {

    private Instant date;

    private String emotion;
}
