package com.mindharbor.modules.user.service.impl;

import com.mindharbor.modules.user.entity.User;
import com.mindharbor.modules.user.mapper.UserMapper;
import com.mindharbor.modules.user.service.UserStatusService;
import org.springframework.stereotype.Service;

/** 账号状态查询实现，只做用户表查询和状态判断。 */
@Service
public class UserStatusServiceImpl implements UserStatusService {

    private final UserMapper mapper;

    public UserStatusServiceImpl(UserMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public boolean isActive(Long userId) {
        if (userId == null) {
            return false;
        }
        return mapper.findById(userId)
                .map(user -> user.getStatus() == User.Status.ACTIVE)
                .orElse(false);
    }
}
