package com.kqa.repository;

import com.kqa.entity.DocumentVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentVersionRepository extends JpaRepository<DocumentVersion, Long> {

    // 查询某文档的所有版本, 按版本号降序 (最新在前)
    List<DocumentVersion> findByDocumentIdOrderByVersionNumberDesc(Long documentId);

    // 查询某文档的最大版本号
    Optional<DocumentVersion> findTopByDocumentIdOrderByVersionNumberDesc(Long documentId);

    // 按文档ID + 版本号精确查找
    Optional<DocumentVersion> findByDocumentIdAndVersionNumber(Long documentId, Integer versionNumber);

    // 统计某文档的版本数
    int countByDocumentId(Long documentId);
}
