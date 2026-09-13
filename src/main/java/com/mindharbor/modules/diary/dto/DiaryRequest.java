package com.mindharbor.modules.diary.dto;

import com.mindharbor.modules.diary.entity.Diary.Emotion;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class DiaryRequest {
    @NotBlank(message = "日记内容不能为空")
    @Size(max = 10000, message = "日记内容不能超过 10000 个字符")
    private String content;

    @NotNull(message = "情绪标签不能为空")
    private Emotion emotion;
}
