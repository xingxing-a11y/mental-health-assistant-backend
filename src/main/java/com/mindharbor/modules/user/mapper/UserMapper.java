package com.mindharbor.modules.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mindharbor.modules.user.entity.User;
import java.time.Instant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import java.util.Optional;

@Mapper
public interface UserMapper extends BaseMapper<User> {

    @Select("SELECT * FROM users WHERE id = #{id} LIMIT 1")
    Optional<User> findById(Long id);

    @Select("SELECT * FROM users WHERE username = #{username} LIMIT 1")
    Optional<User> findByUsername(String username);

    /**
     * 注销账号：只改写身份字段并置为 CLOSED，会话、日记与测评数据保持不变。
     * 必须显式写 SQL，MyBatis-Plus 的 updateById 会忽略 null 字段，无法清空资料列。
     */
    @Update("UPDATE users SET status = 'CLOSED', deactivated_at = #{deactivatedAt}, "
            + "username = #{username}, nickname = NULL, avatar_url = NULL, gender = NULL, age = NULL, "
            + "failed_login_attempts = 0, locked_until = NULL "
            + "WHERE id = #{id}")
    int markDeactivated(
            @Param("id") Long id,
            @Param("username") String username,
            @Param("deactivatedAt") Instant deactivatedAt);

    /**
     * 换头像的条件更新：仅当库内头像仍是本次读到的旧值时写入新值。
     * 影响行数为 0 说明另一请求已先改走，调用方据此判定并发冲突。
     *
     * <p>必须用 {@code <=>}（NULL 安全等于）而非 {@code =}：首次上传时 avatar_url 为 NULL，
     * 而 {@code avatar_url = NULL} 恒为 false，会导致第一次上传永远失败。
     *
     * @return 影响行数，0 表示旧值已被其它请求改写
     */
    @Update("UPDATE users SET avatar_url = #{newUrl} "
            + "WHERE id = #{id} AND avatar_url <=> #{expectedOldUrl}")
    int updateAvatarIfUnchanged(
            @Param("id") Long id,
            @Param("newUrl") String newUrl,
            @Param("expectedOldUrl") String expectedOldUrl);
}
