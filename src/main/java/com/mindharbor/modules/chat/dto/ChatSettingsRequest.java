package com.mindharbor.modules.chat.dto;

import com.mindharbor.modules.chat.entity.ChatSession.Mode;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ChatSettingsRequest {
    @NotNull
    private Mode mode;
}
