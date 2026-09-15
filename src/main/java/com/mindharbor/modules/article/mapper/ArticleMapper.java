package com.mindharbor.modules.article.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mindharbor.modules.article.entity.Article;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import java.util.List;
import java.util.Optional;

@Mapper
public interface ArticleMapper extends BaseMapper<Article> {
    @Select("SELECT * FROM articles WHERE id = #{id} LIMIT 1")
    Optional<Article> findById(Long id);
    @Select("SELECT * FROM articles ORDER BY updated_at DESC")
    List<Article> findAllByOrderByUpdatedAtDesc();

    @Select("SELECT * FROM articles WHERE LOWER(title) LIKE LOWER(CONCAT('%', #{keyword}, '%')) ORDER BY updated_at DESC")
    List<Article> findByTitleContainingIgnoreCaseOrderByUpdatedAtDesc(String keyword);
}
