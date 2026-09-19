package com.mindharbor.modules.diary.vo;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** AI 情绪日记分析结果，替代 Controller 里用 Map 拼装的响应体。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DiaryAnalysisVO {

    private String analysis;
}
