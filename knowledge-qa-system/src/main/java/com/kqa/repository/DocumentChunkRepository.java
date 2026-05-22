package com.kqa.repository;

import com.kqa.entity.DocumentChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, Long> {

    // 查询某个文档某个版本的所有chunk, 按序号排序
    List<DocumentChunk> findByDocumentIdAndVersionOrderByChunkIndexAsc(Long documentId, Integer version);

    // 统计某文档某版本的chunk数量
    int countByDocumentIdAndVersion(Long documentId, Integer version);

    // 删除某文档某版本的所有chunk (版本更新时先删旧chunk)
    void deleteByDocumentIdAndVersion(Long documentId, Integer version);

    // 查询某文档的所有chunk(不限版本)
    List<DocumentChunk> findByDocumentId(Long documentId);

    // 按 Milvus ID 查询向量对应的chunk
    List<DocumentChunk> findByMilvusIdIn(List<Long> milvusIds);
}
