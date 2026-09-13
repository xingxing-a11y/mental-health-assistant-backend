package com.mindharbor.modules.chat.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "chat_messages")
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)

    private Long id;

    @ManyToOne(optional = false)

    private ChatSession session;

    @Enumerated(EnumType.STRING)

    private Role role;

    @Lob
    @Column(nullable = false, columnDefinition = "longtext")

    private String content;

    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private boolean favorite;

    public enum Role {
        USER,
        ASSISTANT
    }
}


