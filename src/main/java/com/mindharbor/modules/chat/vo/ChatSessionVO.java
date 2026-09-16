package com.mindharbor.modules.chat.vo;

import java.time.Instant;

import com.mindharbor.modules.chat.entity.ChatSession;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ChatSessionVO {

    private Long id;

    private String name;

    private String preview;

    private Instant latestMessageAt;

    private ChatSession.Mode mode;

    private boolean pinned;

    private boolean archived;
}
