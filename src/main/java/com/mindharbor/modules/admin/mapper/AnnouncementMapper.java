package com.mindharbor.modules.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mindharbor.modules.admin.entity.Announcement;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import java.util.List;
import java.util.Optional;

@Mapper
public interface AnnouncementMapper extends BaseMapper<Announcement> {
    @Select("SELECT * FROM announcements ORDER BY created_at DESC")
    List<Announcement> findAllByOrderByCreatedAtDesc();

    @Select("SELECT * FROM announcements WHERE id = #{id} LIMIT 1")
    Optional<Announcement> findById(Long id);
}
