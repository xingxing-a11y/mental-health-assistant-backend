package com.mindharbor.modules.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mindharbor.modules.system.entity.SystemSetting;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/** 系统配置数据访问层。 */
@Mapper
public interface SystemSettingMapper extends BaseMapper<SystemSetting> {

    @Select("SELECT * FROM system_settings")
    List<SystemSetting> findAll();
}
