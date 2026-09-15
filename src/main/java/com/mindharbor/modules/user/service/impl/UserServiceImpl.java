package com.mindharbor.modules.user.service.impl;

import com.mindharbor.modules.user.dto.LoginRequest;
import com.mindharbor.modules.user.dto.RegisterRequest;
import com.mindharbor.modules.user.dto.PasswordUpdateRequest;
import com.mindharbor.modules.user.dto.ProfileUpdateRequest;
import com.mindharbor.modules.user.dto.UserStatusRequest;
import com.mindharbor.modules.user.entity.User;
import com.mindharbor.modules.user.mapper.UserMapper;
import com.mindharbor.modules.user.service.UserService;
import com.mindharbor.modules.user.vo.UserVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;
import java.util.List;
import java.time.Instant;
import com.mindharbor.modules.user.vo.PageResult;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.mindharbor.security.CurrentUserProvider;
import org.springframework.dao.DuplicateKeyException;

@Service
public class UserServiceImpl implements UserService {

    private static final int MAX_LOGIN_ATTEMPTS = 5;

    private static final long LOCK_MINUTES = 15;

    private final UserMapper mapper;
    private final PasswordEncoder passwordEncoder;
    private final CurrentUserProvider currentUserProvider;

    public UserServiceImpl(
            UserMapper mapper,
            PasswordEncoder passwordEncoder,
            CurrentUserProvider currentUserProvider) {
        this.mapper = mapper;
        this.passwordEncoder = passwordEncoder;
        this.currentUserProvider = currentUserProvider;
    }

    @Override
    public UserVO register(RegisterRequest request) {
        String username = request.getUsername() == null
                ? ""
                : request.getUsername().trim();

        if (request.getPassword() == null
                || !request.getPassword().equals(request.getConfirmPassword())) {
            throw new IllegalArgumentException("密码确认不一致");
        }

        if (mapper.findByUsername(username).isPresent()) {
            throw new IllegalArgumentException("账号已存在");
        }

        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setNickname(request.getNickname());
        user.setGender(request.getGender());
        user.setAge(request.getAge());
        try {
            mapper.insert(user);
        } catch (DuplicateKeyException exception) {
            throw new IllegalArgumentException("账号已存在");
        }
        return toView(user);
    }

    @Override
    public UserVO login(LoginRequest request) {
        User user = mapper.findByUsername(request.getUsername())
                .orElseThrow(() -> new IllegalArgumentException("用户名或密码错误"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            int attempts = user.getFailedLoginAttempts() == null
                    ? 0
                    : user.getFailedLoginAttempts();
            user.setFailedLoginAttempts(attempts + 1);

            if (user.getFailedLoginAttempts() >= MAX_LOGIN_ATTEMPTS) {
                user.setLockedUntil(Instant.now().plusSeconds(LOCK_MINUTES * 60));
                user.setFailedLoginAttempts(0);
            }

            mapper.updateById(user);
            throw new IllegalArgumentException("用户名或密码错误");
        }

        if (user.getStatus() == User.Status.DISABLED) {
            throw new IllegalArgumentException("账号已被禁用");
        }

        if (user.getLockedUntil() != null
                && user.getLockedUntil().isAfter(Instant.now())) {
            throw new IllegalArgumentException("登录失败次数过多，请稍后再试");
        }

        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        mapper.updateById(user);
        return toView(user);
    }

    @Override
    public UserVO current() {
        return mapper.findById(currentUserProvider.getUserId())
                .map(this::toView)
                .orElse(null);
    }

    @Override
    public UserVO updateProfile(ProfileUpdateRequest request) {
        User user = currentEntity();
        user.setNickname(request.getNickname());
        user.setGender(request.getGender());
        user.setAge(request.getAge());
        mapper.updateById(user);
        return toView(user);
    }

    @Override
    public void updatePassword(PasswordUpdateRequest request) {
        User user = currentEntity();
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("旧密码错误");
        }
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        mapper.updateById(user);
    }

    private User currentEntity() {
        return mapper.findById(currentUserProvider.getUserId())
                .orElseThrow(() -> new IllegalStateException("当前没有可用用户"));
    }

    private UserVO toView(User user) {
        UserVO view = new UserVO();
        view.setId(user.getId());
        view.setUsername(user.getUsername());
        view.setNickname(user.getNickname());
        view.setGender(user.getGender());
        view.setAge(user.getAge());
        view.setRole(user.getRole().name());
        view.setStatus(user.getStatus().name());
        return view;
    }

    @Override
    public List<UserVO> listUsers() {
        return mapper.selectList(null)
                .stream()
                .map(this::toView)
                .toList();
    }

    @Override
    public PageResult<UserVO> listUsers(int page, int size) {
        return listUsers(page, size, null);
    }

    @Override
    public PageResult<UserVO> listUsers(int page, int size, String nickname) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        String keyword = nickname == null ? "" : nickname.trim();
        if (keyword.length() > 100) {
            keyword = keyword.substring(0, 100);
        }
        LambdaQueryWrapper<User> query = new LambdaQueryWrapper<>();
        if (!keyword.isEmpty()) {
            query.like(User::getNickname, keyword);
        }
        query.orderByDesc(User::getCreatedAt);
        List<User> users = mapper.selectList(query);
        int from = Math.min(safePage * safeSize, users.size());
        int to = Math.min(from + safeSize, users.size());
        List<User> pageUsers = users.subList(from, to);
        long total = users.size();
        long totalPages = (total + safeSize - 1) / safeSize;
        return new PageResult<>(
                pageUsers.stream().map(this::toView).toList(),
                safePage,
                safeSize,
                total,
                (int) totalPages);
    }

    @Override
    public UserVO updateStatus(Long userId, UserStatusRequest request) {
        User user = mapper.findById(userId).orElseThrow();
        user.setStatus(request.getStatus());
        mapper.updateById(user);
        return toView(user);
    }
}
