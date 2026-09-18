package com.mindharbor.modules.notification.mapper;

import java.time.Instant;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 通知已读关系数据访问层，主键为（通知编号, 用户编号）。 */
@Mapper
public interface NotificationReadMapper {

    @Insert("INSERT INTO notification_reads (notification_id, user_id, read_at) "
            + "VALUES (#{notificationId}, #{userId}, #{readAt}) "
            + "ON DUPLICATE KEY UPDATE read_at = #{readAt}")
    int markRead(
            @Param("notificationId") Long notificationId,
            @Param("userId") Long userId,
            @Param("readAt") Instant readAt);

    /** 全部已读用一条 INSERT ... SELECT 完成，避免逐条写入。 */
    @Insert("INSERT INTO notification_reads (notification_id, user_id, read_at) "
            + "SELECT id, #{userId}, #{readAt} FROM notifications "
            + "WHERE (scope = 'ALL' OR target_user_id = #{userId}) "
            + "AND (expire_at IS NULL OR expire_at > #{readAt}) "
            + "ON DUPLICATE KEY UPDATE read_at = #{readAt}")
    int markAllRead(@Param("userId") Long userId, @Param("readAt") Instant readAt);
}
