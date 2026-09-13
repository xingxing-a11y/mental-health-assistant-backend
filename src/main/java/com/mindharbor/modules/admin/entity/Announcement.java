package com.mindharbor.modules.admin.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "announcements")
public class Announcement {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 200)
    private String title;
    @Lob @Column(nullable = false, columnDefinition = "longtext")
    private String content;
    @Column(nullable = false)
    private Instant createdAt = Instant.now();
}
