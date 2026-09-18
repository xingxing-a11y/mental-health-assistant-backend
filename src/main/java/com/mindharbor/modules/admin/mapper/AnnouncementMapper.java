package com.mindharbor.modules.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mindharbor.modules.admin.entity.Announcement;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/** 系统公告数据访问层，只声明查询语句，不承载业务判断。 */
@Mapper
public interface AnnouncementMapper extends BaseMapper<Announcement> {

    @Select("SELECT * FROM announcements ORDER BY created_at DESC")
    List<Announcement> findAllByOrderByCreatedAtDesc();

    @Select("SELECT * FROM announcements WHERE id = #{id} LIMIT 1")
    Optional<Announcement> findById(Long id);
}
