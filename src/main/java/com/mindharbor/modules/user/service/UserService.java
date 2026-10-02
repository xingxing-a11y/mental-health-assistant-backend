package com.mindharbor.modules.user.service;

import com.mindharbor.common.PageResult;
import com.mindharbor.common.storage.AvatarContent;
import com.mindharbor.modules.user.dto.LoginRequest;
import com.mindharbor.modules.user.dto.RegisterRequest;
import com.mindharbor.modules.user.dto.PasswordUpdateRequest;
import com.mindharbor.modules.user.dto.ProfileUpdateRequest;
import com.mindharbor.modules.user.dto.UserStatusRequest;
import com.mindharbor.modules.user.vo.UserVO;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

public interface UserService {

    UserVO register(RegisterRequest request);

    UserVO login(LoginRequest request);

    UserVO current();

    UserVO updateProfile(ProfileUpdateRequest request);

    UserVO updateAvatar(MultipartFile file);

    /** 读取头像文件；文件名非法或文件不存在都按不存在处理，供代理转发使用。 */
    AvatarContent loadAvatar(String filename);

    void updatePassword(PasswordUpdateRequest request);

    /** 注销当前账号：只做匿名化，不删除会话、日记与测评记录。 */
    void deactivate();

    List<UserVO> listUsers();

    PageResult<UserVO> listUsers(int page, int size);

    PageResult<UserVO> listUsers(int page, int size, String nickname);

    UserVO updateStatus(Long userId, UserStatusRequest request);
}
