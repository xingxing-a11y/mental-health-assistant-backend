package com.mindharbor.modules.chat.entity;

import com.mindharbor.modules.user.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "chat_sessions")
public class ChatSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)

    private Long id;

    @ManyToOne(optional = false)

    private User user;

    @Column(nullable = false)

    private String name;

    private String preview;

    private Instant latestMessageAt = Instant.now();

    private boolean contextCleared;

    private boolean highRisk;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Mode mode = Mode.COMPANION;

    public enum Mode {
        COMPANION,
        ANALYTICAL
    }
}


