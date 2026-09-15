package com.mindharbor.modules.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mindharbor.modules.user.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import java.util.Optional;

@Mapper
public interface UserMapper extends BaseMapper<User> {

    @Select("SELECT * FROM users WHERE id = #{id} LIMIT 1")
    Optional<User> findById(Long id);

    @Select("SELECT * FROM users WHERE username = #{username} LIMIT 1")
    Optional<User> findByUsername(String username);

}
