package com.mindharbor.modules.user.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mindharbor.modules.user.dto.LoginRequest;
import com.mindharbor.modules.user.entity.User;
import com.mindharbor.modules.user.mapper.UserMapper;
import com.mindharbor.modules.user.vo.UserVO;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 登录失败锁定：拦截时机与计数复位。
 *
 * <p>这里守的是一条容易被改回去的次序——锁定检查必须排在 BCrypt 校验<b>之前</b>。
 * 一旦排到后面，错误密码会先进入失败分支，锁定就只拦得住本人用正确密码登录，
 * 对持续爆破完全无效，而且从代码上看不出来。因此固定成用例。
 */
@DisplayName("登录失败锁定")
class UserServiceImplLoginTest {

    private static final PasswordEncoder ENCODER = new BCryptPasswordEncoder();

    @Test
    @DisplayName("已锁定账号即使密码错误也返回锁定提示：锁定先于密码校验")
    void lockedAccountRejectsEvenWrongPassword() {
        FakeUserMapper fake = new FakeUserMapper();
        fake.user = user("alice", "correct-password");
        fake.user.setFailedLoginAttempts(3);
        fake.user.setLockedUntil(Instant.now().plusSeconds(600));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> serviceWith(fake).login(new LoginRequest("alice", "wrong-password")));

        assertEquals("登录失败次数过多，请稍后再试", error.getMessage(),
                "锁定检查若排在密码校验之后，错误密码会先走失败分支，锁定形同虚设");
        assertTrue(fake.calls.isEmpty(),
                "锁定期内不得再写失败次数，否则攻击者能无限延长受害者的锁定期");
    }

    @Test
    @DisplayName("未锁定账号密码错误时失败次数加一，且不写锁定时间")
    void wrongPasswordIncrementsAttempts() {
        FakeUserMapper fake = new FakeUserMapper();
        fake.user = user("alice", "correct-password");
        fake.user.setFailedLoginAttempts(2);

        assertThrows(
                IllegalArgumentException.class,
                () -> serviceWith(fake).login(new LoginRequest("alice", "wrong-password")));

        assertEquals(List.of("updateLoginFailures"), fake.calls);
        assertEquals(3, fake.lastAttempts);
        assertNull(fake.lastLockedUntil);
    }

    @Test
    @DisplayName("第 5 次失败写入将来的锁定时间，并把计数归零")
    void fifthFailureLocksAccount() {
        FakeUserMapper fake = new FakeUserMapper();
        fake.user = user("alice", "correct-password");
        fake.user.setFailedLoginAttempts(4);

        assertThrows(
                IllegalArgumentException.class,
                () -> serviceWith(fake).login(new LoginRequest("alice", "wrong-password")));

        assertEquals(0, fake.lastAttempts);
        assertNotNull(fake.lastLockedUntil);
        assertTrue(fake.lastLockedUntil.isAfter(Instant.now()), "锁定截止时间必须在将来");
    }

    @Test
    @DisplayName("锁定到期后正确密码登录成功，并复位失败次数与锁定时间")
    void expiredLockAllowsLoginAndResetsState() {
        FakeUserMapper fake = new FakeUserMapper();
        fake.user = user("alice", "correct-password");
        fake.user.setFailedLoginAttempts(0);
        fake.user.setLockedUntil(Instant.now().minusSeconds(60));

        UserVO view = serviceWith(fake).login(new LoginRequest("alice", "correct-password"));

        assertEquals("alice", view.getUsername());
        assertEquals(0, fake.lastAttempts);
        assertNull(fake.lastLockedUntil,
                "必须显式写 SQL 才能把 locked_until 置空，updateById 会跳过 null 字段");
    }

    @Test
    @DisplayName("账号不存在时给出与密码错误一致的提示，且不写任何计数")
    void unknownUsernameGivesSameMessageAsWrongPassword() {
        FakeUserMapper fake = new FakeUserMapper();

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> serviceWith(fake).login(new LoginRequest("nobody", "whatever")));

        assertEquals("用户名或密码错误", error.getMessage(), "不能借提示语区分账号是否存在");
        assertTrue(fake.calls.isEmpty());
    }

    @Test
    @DisplayName("禁用账号用正确密码登录被拒，且不被记成一次密码失败")
    void disabledAccountIsRejected() {
        FakeUserMapper fake = new FakeUserMapper();
        fake.user = user("alice", "correct-password");
        fake.user.setStatus(User.Status.DISABLED);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> serviceWith(fake).login(new LoginRequest("alice", "correct-password")));

        assertEquals("账号已被禁用", error.getMessage());
        assertTrue(fake.calls.isEmpty());
    }

    private static User user(String username, String rawPassword) {
        User user = new User();
        user.setId(1L);
        user.setUsername(username);
        user.setPasswordHash(ENCODER.encode(rawPassword));
        return user;
    }

    /** login 只用到 mapper，其余协作者传 null：它们不参与这条路径。 */
    private static UserServiceImpl serviceWith(FakeUserMapper fake) {
        return new UserServiceImpl(fake.asMapper(), ENCODER, null, null, null, null);
    }

    /**
     * UserMapper 继承 BaseMapper，方法上百个，用动态代理只实现登录路径真正调用的两条。
     * 另外补上 updateById：它在修复前的实现里会被调用，留个记录便于失败时看清走向。
     */
    private static final class FakeUserMapper {

        private User user;

        private final List<String> calls = new ArrayList<>();

        private int lastAttempts = -1;

        private Instant lastLockedUntil;

        private UserMapper asMapper() {
            return (UserMapper) Proxy.newProxyInstance(
                    UserServiceImplLoginTest.class.getClassLoader(),
                    new Class<?>[] {UserMapper.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "findByUsername" -> Optional.ofNullable(user);
                        case "updateLoginFailures" -> {
                            calls.add("updateLoginFailures");
                            lastAttempts = (Integer) args[1];
                            lastLockedUntil = (Instant) args[2];
                            yield 1;
                        }
                        case "updateById" -> {
                            calls.add("updateById");
                            yield 1;
                        }
                        default -> throw new UnsupportedOperationException(method.getName());
                    });
        }
    }
}
