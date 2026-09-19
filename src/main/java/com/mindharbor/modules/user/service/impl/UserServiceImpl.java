package com.mindharbor.modules.user.service.impl;

import com.mindharbor.common.ConflictException;
import com.mindharbor.common.ResourceNotFoundException;
import com.mindharbor.common.storage.AvatarContent;
import com.mindharbor.common.storage.AvatarStorage;
import com.mindharbor.common.storage.CountingInputStream;
import com.mindharbor.modules.admin.entity.AdminOperationLog;
import com.mindharbor.modules.admin.service.AdminOperationLogService;
import com.mindharbor.modules.user.dto.LoginRequest;
import com.mindharbor.modules.user.dto.RegisterRequest;
import com.mindharbor.modules.user.dto.PasswordUpdateRequest;
import com.mindharbor.modules.user.dto.ProfileUpdateRequest;
import com.mindharbor.modules.user.dto.UserStatusRequest;
import com.mindharbor.modules.user.entity.User;
import com.mindharbor.modules.user.mapper.UserMapper;
import com.mindharbor.modules.user.service.UserService;
import com.mindharbor.modules.user.service.UserSettingsService;
import com.mindharbor.modules.user.vo.UserVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.time.Instant;
import com.mindharbor.modules.user.vo.PageResult;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.mindharbor.security.CurrentUserProvider;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class UserServiceImpl implements UserService {

    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);

    private static final int MAX_LOGIN_ATTEMPTS = 5;

    private static final long LOCK_MINUTES = 15;

    private static final long MAX_AVATAR_BYTES = 2 * 1024 * 1024;

    /** 头像文件名由服务端生成，固定为 UUID + 扩展名，据此拒绝任何拼接出的越权路径。 */
    private static final Pattern AVATAR_FILENAME_PATTERN = Pattern.compile(
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(?:jpg|png|webp)");

    private final UserMapper mapper;
    private final PasswordEncoder passwordEncoder;
    private final CurrentUserProvider currentUserProvider;
    private final UserSettingsService userSettingsService;
    private final AdminOperationLogService operationLogService;
    private final AvatarStorage avatarStorage;

    public UserServiceImpl(
            UserMapper mapper,
            PasswordEncoder passwordEncoder,
            CurrentUserProvider currentUserProvider,
            UserSettingsService userSettingsService,
            AdminOperationLogService operationLogService,
            AvatarStorage avatarStorage) {
        this.mapper = mapper;
        this.passwordEncoder = passwordEncoder;
        this.currentUserProvider = currentUserProvider;
        this.userSettingsService = userSettingsService;
        this.operationLogService = operationLogService;
        this.avatarStorage = avatarStorage;
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

        if (user.getStatus() == User.Status.CLOSED) {
            throw new IllegalArgumentException("账号已注销，无法登录");
        }

        if (user.getStatus() != User.Status.ACTIVE) {
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
        if (request.getGender() != null
                && !request.getGender().isBlank()
                && !List.of("男", "女", "不愿透露").contains(request.getGender())) {
            throw new IllegalArgumentException("性别选项无效");
        }
        user.setNickname(request.getNickname());
        user.setGender(request.getGender());
        user.setAge(request.getAge());
        mapper.updateById(user);
        return toView(user);
    }

    @Override
    public UserVO updateAvatar(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择头像文件");
        }
        if (file.getSize() > MAX_AVATAR_BYTES) {
            throw new IllegalArgumentException("头像大小不能超过 2MB");
        }
        String contentType = file.getContentType() == null
                ? ""
                : file.getContentType().toLowerCase(Locale.ROOT);
        String extension = switch (contentType) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> throw new IllegalArgumentException("仅支持 JPG、PNG 或 WEBP 图片");
        };

        String filename = UUID.randomUUID() + extension;
        // 先读满并计数：对象存储必须在写入前就知道长度，且这样能在落盘/上传前拦掉超限请求。
        byte[] bytes = readWithinLimit(file);
        String newUrl = avatarStorage.save(
                new ByteArrayInputStream(bytes), bytes.length, filename, contentType);
        if (!newUrl.startsWith(AvatarStorage.URL_PREFIX + "/")) {
            throw new IllegalStateException("存储实现返回了非法的访问路径");
        }

        User user = currentEntity();
        String previousUrl = user.getAvatarUrl();
        // 条件更新：旧值被其它请求改走时影响行数为 0，避免把对方的头像当成自己的旧头像删除。
        if (mapper.updateAvatarIfUnchanged(user.getId(), newUrl, previousUrl) == 0) {
            // 必须先清理本次上传的对象再抛异常，否则方法中断后它会成为无人引用的孤儿。
            deleteAvatarResource(newUrl, user.getId());
            throw new ConflictException("头像已被其它请求更新，请刷新后重试");
        }

        deleteAvatarResource(previousUrl, user.getId());
        // 条件更新只写入 avatar_url 一列，内存中的 user 其余字段可能已过期，重新查库再组装响应。
        return toView(currentEntity());
    }

    /**
     * 读满上传流并返回字节内容，同时用计数流确认真实长度不超过上限。
     * 之所以不直接流式交给存储层：对象存储需要预先拿到长度，而长度只有读完才知道。
     */
    private byte[] readWithinLimit(MultipartFile file) {
        try (CountingInputStream counting =
                     new CountingInputStream(file.getInputStream(), MAX_AVATAR_BYTES)) {
            return counting.readAllBytes();
        } catch (IOException exception) {
            throw new IllegalArgumentException("头像文件读取失败或超过大小限制", exception);
        }
    }

    @Override
    public AvatarContent loadAvatar(String filename) {
        if (!isAvatarFilename(filename)) {
            throw new ResourceNotFoundException("头像不存在");
        }
        return avatarStorage.load(filename);
    }

    /**
     * 清理服务端生成的头像文件，只做路径形态校验，<b>不做归属校验</b>，调用方必须保证传入的是该用户的 avatarUrl。
     * 删除失败只记日志、不阻断主流程，但要带上 userId 与 filename，便于回溯孤儿对象。
     */
    private void deleteAvatarResource(String avatarUrl, Long userId) {
        String prefix = AvatarStorage.URL_PREFIX + "/";
        if (avatarUrl == null || !avatarUrl.startsWith(prefix)) {
            return;
        }
        String filename = avatarUrl.substring(prefix.length());
        if (!isAvatarFilename(filename)) {
            return;
        }
        try {
            avatarStorage.delete(filename);
        } catch (RuntimeException exception) {
            log.warn("删除头像资源失败，userId={}，filename={}", userId, filename, exception);
        }
    }

    private boolean isAvatarFilename(String filename) {
        return filename != null && AVATAR_FILENAME_PATTERN.matcher(filename).matches();
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

    @Override
    public void deactivate() {
        User user = currentEntity();
        String previousAvatar = user.getAvatarUrl();
        String anonymizedUsername = "closed_" + user.getId();
        userSettingsService.disableDataConsent(user.getId());
        mapper.markDeactivated(user.getId(), anonymizedUsername, Instant.now());
        // 注销后 avatar_url 已置空，孤儿对象再无记录可说明归属，因此日志必须带 userId。
        deleteAvatarResource(previousAvatar, user.getId());
    }

    private UserVO toView(User user) {
        UserVO view = new UserVO();
        view.setId(user.getId());
        view.setUsername(user.getUsername());
        view.setNickname(user.getNickname());
        view.setAvatarUrl(user.getAvatarUrl());
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
        User user = mapper.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("用户不存在"));
        user.setStatus(request.getStatus());
        mapper.updateById(user);
        operationLogService.record(
                AdminOperationLog.Type.USER,
                "user",
                user.getId(),
                request.getStatus() == User.Status.DISABLED ? "禁用账号" : "启用账号");
        return toView(user);
    }
}
