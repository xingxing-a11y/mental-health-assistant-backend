package com.mindharbor.modules.user.service;

import com.mindharbor.modules.user.dto.LoginRequest;
import com.mindharbor.modules.user.dto.RegisterRequest;
import com.mindharbor.modules.user.dto.PasswordUpdateRequest;
import com.mindharbor.modules.user.dto.ProfileUpdateRequest;
import com.mindharbor.modules.user.dto.UserStatusRequest;
import com.mindharbor.modules.user.vo.UserVO;
import com.mindharbor.modules.user.vo.PageResult;
import java.util.List;

public interface UserService {

    UserVO register(RegisterRequest request);

    UserVO login(LoginRequest request);

    UserVO current();

    UserVO updateProfile(ProfileUpdateRequest request);

    void updatePassword(PasswordUpdateRequest request);

    List<UserVO> listUsers();

    PageResult<UserVO> listUsers(int page, int size);

    UserVO updateStatus(Long userId, UserStatusRequest request);
}


