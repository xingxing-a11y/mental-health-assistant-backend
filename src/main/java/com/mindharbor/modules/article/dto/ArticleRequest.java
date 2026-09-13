package com.mindharbor.modules.article.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ArticleRequest {
    @NotBlank
    @Size(max = 200)
    private String title;

    @Size(max = 500)
    private String summary;

    @NotBlank
    @Size(max = 200000)
    private String content;
}
