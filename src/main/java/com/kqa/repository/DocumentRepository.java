package com.kqa.repository;

import com.kqa.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {

    // 按状态查询: 查询待处理/处理中的文档
    List<Document> findByStatus(String status);

    // 按分类查询
    List<Document> findByCategoryId(Long categoryId);

    // 按标题模糊搜索 (Spring Data JPA 自动生成 LIKE 查询)
    List<Document> findByTitleContaining(String keyword);

    // 统计某分类下的文档数
    long countByCategoryId(Long categoryId);

    // 查询某状态的文档数
    long countByStatus(String status);

    // 按上传时间降序获取最新文档
    List<Document> findAllByOrderByUploadTimeDesc();
}
