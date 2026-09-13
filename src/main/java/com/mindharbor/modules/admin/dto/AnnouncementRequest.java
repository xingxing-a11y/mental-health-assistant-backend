package com.mindharbor.modules.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @NoArgsConstructor
public class AnnouncementRequest {
    @NotBlank @Size(max = 200)
    private String title;
    @NotBlank @Size(max = 10000)
    private String content;
}
