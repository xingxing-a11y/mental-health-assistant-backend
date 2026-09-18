package com.mindharbor.modules.chat.vo;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 敏感内容判定结果，只给出级别与统一提示文案，不回显命中的原始词。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SensitiveCheckVO {

    private boolean triggered;

    private Level level;

    private String prompt;

    private String hotline;

    public enum Level {
        NONE,
        CARE,
        HIGH
    }
}
