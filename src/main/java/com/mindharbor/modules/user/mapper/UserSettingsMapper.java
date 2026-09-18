package com.mindharbor.modules.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mindharbor.modules.user.entity.UserSettings;
import java.util.Optional;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/** 个人偏好设置数据访问层，只声明查询语句。 */
@Mapper
public interface UserSettingsMapper extends BaseMapper<UserSettings> {

    @Select("SELECT * FROM user_settings WHERE user_id = #{userId} LIMIT 1")
    Optional<UserSettings> findByUserId(Long userId);

    /** 按消息保存策略筛选用户，供定时清理任务遍历，不返回任何偏好明细。 */
    @Select("SELECT user_id FROM user_settings WHERE message_retention = #{retention}")
    List<Long> findUserIdsByMessageRetention(String retention);
}
