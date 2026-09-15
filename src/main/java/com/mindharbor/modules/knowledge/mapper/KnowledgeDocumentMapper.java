package com.mindharbor.modules.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mindharbor.modules.knowledge.entity.KnowledgeDocument;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import java.util.List;
import java.util.Optional;

@Mapper
public interface KnowledgeDocumentMapper extends BaseMapper<KnowledgeDocument> {
    @Select("SELECT * FROM knowledge_documents")
    List<KnowledgeDocument> findAll();

    @Select("SELECT * FROM knowledge_documents WHERE id = #{id} LIMIT 1")
    Optional<KnowledgeDocument> findById(Long id);
}
