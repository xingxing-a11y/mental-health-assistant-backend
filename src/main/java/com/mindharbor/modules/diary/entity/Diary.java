package com.mindharbor.modules.diary.entity;

import com.mindharbor.modules.user.entity.User;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "diaries")
public class Diary {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private User user;

    @Lob
    @Column(nullable = false, columnDefinition = "longtext")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Emotion emotion;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    public enum Emotion {
        HAPPY, CALM, STRESSED, ANXIOUS, LOW
    }
}
