package com.mindharbor.modules.user.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "users", uniqueConstraints = @UniqueConstraint(columnNames = "username"))
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)

    private Long id;

    @Column(nullable = false)

    private String username;

    @Column(nullable = false)

    private String passwordHash;

    private String nickname;

    private String gender;

    private Integer age;

    @Enumerated(EnumType.STRING)

    private Role role = Role.USER;

    @Enumerated(EnumType.STRING)

    private Status status = Status.ACTIVE;

    private Integer failedLoginAttempts = 0;

    private Instant lockedUntil;

    private Instant createdAt = Instant.now();

    public enum Role {
        USER,
        ADMIN
    }

    public enum Status {
        ACTIVE,
        DISABLED
    }
}


