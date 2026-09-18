package com.mindharbor.modules.notification.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mindharbor.modules.notification.entity.Notification;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 通知数据访问层，可见性与过期过滤放在 SQL 内，提示文案判断放在 service 层。 */
@Mapper
public interface NotificationMapper extends BaseMapper<Notification> {

    @Select("SELECT * FROM notifications WHERE id = #{id} LIMIT 1")
    Optional<Notification> findById(Long id);

    /** 一次 LEFT JOIN 取已读状态，避免整表扇出写入。 */
    @Select("SELECT n.*, r.read_at FROM notifications n "
            + "LEFT JOIN notification_reads r "
            + "ON r.notification_id = n.id AND r.user_id = #{userId} "
            + "WHERE (n.scope = 'ALL' OR n.target_user_id = #{userId}) "
            + "AND (n.expire_at IS NULL OR n.expire_at > #{now}) "
            + "ORDER BY n.created_at DESC, n.id DESC "
            + "LIMIT #{limit} OFFSET #{offset}")
    List<Notification> findVisible(
            @Param("userId") Long userId,
            @Param("now") Instant now,
            @Param("limit") int limit,
            @Param("offset") int offset);

    @Select("SELECT COUNT(*) FROM notifications n "
            + "WHERE (n.scope = 'ALL' OR n.target_user_id = #{userId}) "
            + "AND (n.expire_at IS NULL OR n.expire_at > #{now})")
    long countVisible(@Param("userId") Long userId, @Param("now") Instant now);

    /** 轻量未读数，供铃铛低频轮询。 */
    @Select("SELECT COUNT(*) FROM notifications n "
            + "LEFT JOIN notification_reads r "
            + "ON r.notification_id = n.id AND r.user_id = #{userId} "
            + "WHERE (n.scope = 'ALL' OR n.target_user_id = #{userId}) "
            + "AND (n.expire_at IS NULL OR n.expire_at > #{now}) "
            + "AND r.read_at IS NULL")
    long countUnread(@Param("userId") Long userId, @Param("now") Instant now);

    @Select("SELECT * FROM notifications ORDER BY created_at DESC, id DESC "
            + "LIMIT #{limit} OFFSET #{offset}")
    List<Notification> findPage(@Param("limit") int limit, @Param("offset") int offset);

    @Select("SELECT COUNT(*) FROM notifications")
    long countAll();

    /** 归档清理按主键批处理，避免一次大事务锁表。 */
    @Select("SELECT id FROM notifications "
            + "WHERE expire_at IS NOT NULL AND expire_at <= #{before} "
            + "ORDER BY id ASC LIMIT #{limit}")
    List<Long> findExpiredIds(@Param("before") Instant before, @Param("limit") int limit);

    /** 已读记录由 notification_reads 的外键级联删除，这里只删主表。 */
    @Delete("<script>"
            + "DELETE FROM notifications WHERE id IN "
            + "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>"
            + "</script>")
    int deleteByIds(@Param("ids") List<Long> ids);
}
