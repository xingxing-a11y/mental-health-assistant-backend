package com.mindharbor.modules.user.service;

/** 账号状态业务：被禁用账号的既有 JWT 必须在过期前立即失效。 */
public interface UserStatusService {

    /** 判断账号是否可用：只有 ACTIVE 视为有效，禁用与已注销账号都必须立即失效。 */
    boolean isActive(Long userId);
}
