package com.mindharbor.modules.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mindharbor.modules.chat.entity.SensitiveEvent;
import org.apache.ibatis.annotations.Mapper;

/** 敏感词命中审计数据访问层，写入内容由 service 层决定。 */
@Mapper
public interface SensitiveEventMapper extends BaseMapper<SensitiveEvent> {
}
