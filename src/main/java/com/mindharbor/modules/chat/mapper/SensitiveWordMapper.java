package com.mindharbor.modules.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mindharbor.modules.chat.entity.SensitiveWord;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/** 敏感词数据访问层，只声明查询语句，命中判定放在 service 层。 */
@Mapper
public interface SensitiveWordMapper extends BaseMapper<SensitiveWord> {

    /** HIGH 排在 CARE 之前，便于 service 优先命中高危词条。 */
    @Select("SELECT * FROM sensitive_words WHERE enabled = 1 ORDER BY level DESC, id ASC")
    List<SensitiveWord> findEnabled();

    @Select("SELECT * FROM sensitive_words ORDER BY level DESC, id ASC")
    List<SensitiveWord> findAll();

    @Select("SELECT * FROM sensitive_words WHERE word = #{word} LIMIT 1")
    Optional<SensitiveWord> findByWord(String word);
}
