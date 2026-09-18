package com.mindharbor.modules.relax.vo;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 冥想引导脚本：前端用浏览器语音合成逐段朗读，不依赖外部音频素材。 */
@Getter
@AllArgsConstructor
public class RelaxMeditationVO {

    private String id;

    private String title;

    private int minutes;

    private String description;

    private List<String> segments;
}
