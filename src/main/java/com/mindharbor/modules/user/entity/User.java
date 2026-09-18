package com.mindharbor.modules.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@TableName("users")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    /** 密码哈希禁止序列化到任何响应体，避免意外泄露。 */
    @JsonIgnore
    private String passwordHash;

    private String nickname;

    private String avatarUrl;

    private String gender;

    private Integer age;

    private Role role = Role.USER;

    private Status status = Status.ACTIVE;

    private Integer failedLoginAttempts = 0;

    private Instant lockedUntil;

    /** 注销时间，仅注销账号有值；注销只做匿名化，不删除业务数据。 */
    private Instant deactivatedAt;

    private Instant createdAt = Instant.now();

    public enum Role {
        USER,
        ADMIN
    }

    public enum Status {
        ACTIVE,
        DISABLED,
        CLOSED
    }
}
