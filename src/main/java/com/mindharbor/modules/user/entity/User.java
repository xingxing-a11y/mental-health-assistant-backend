package com.mindharbor.modules.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@TableName("users")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    private String passwordHash;

    private String nickname;

    private String avatarUrl;

    private String gender;

    private Integer age;

    private Role role = Role.USER;

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


