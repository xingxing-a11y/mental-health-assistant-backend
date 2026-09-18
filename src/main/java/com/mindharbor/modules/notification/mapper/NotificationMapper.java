package com.mindharbor.modules.notification.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mindharbor.modules.notification.entity.Notification;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 通知数据访问层，语句写在 resources/mapper/notification/NotificationMapper.xml。 */
@Mapper
public interface NotificationMapper extends BaseMapper<Notification> {

    Optional<Notification> findById(@Param("id") Long id);

    /** 一次 LEFT JOIN 取已读状态，避免整表扇出写入。 */
    List<Notification> findVisible(
            @Param("userId") Long userId,
            @Param("now") Instant now,
            @Param("limit") int limit,
            @Param("offset") int offset);

    long countVisible(@Param("userId") Long userId, @Param("now") Instant now);

    /** 轻量未读数，供铃铛低频轮询。 */
    long countUnread(@Param("userId") Long userId, @Param("now") Instant now);

    List<Notification> findPage(@Param("limit") int limit, @Param("offset") int offset);

    long countAll();

    /** 归档清理按主键批处理，避免一次大事务锁表。 */
    List<Long> findExpiredIds(@Param("before") Instant before, @Param("limit") int limit);

    /** 已读记录由 notification_reads 的外键级联删除，这里只删主表。 */
    int deleteByIds(@Param("ids") List<Long> ids);
}