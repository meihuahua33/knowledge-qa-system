package com.kqa.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "document_chunks", indexes = {
    @Index(name = "idx_doc_version", columnList = "document_id, version")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "document_id", nullable = false)
    private Long documentId;        // 所属文档

    @Column(nullable = false)
    private Integer version;        // 所属版本号

    @Column(nullable = false)
    private Integer chunkIndex;     // 块序号(0开始)

    @Column(columnDefinition = "LONGTEXT", nullable = false)
    private String content;         // 文本块内容(纯文本)

    @Column(nullable = false)
    private Integer tokenCount;     // Token 数量

    @Column(name = "milvus_id")
    private Long milvusId;          // Milvus 中的向量ID

    @Column(name = "es_id", length = 100)
    private String esId;            // ES 中的文档ID

    @Column(columnDefinition = "TEXT")
    private String metadata;        // JSON: 章节标题、页码等结构信息

    @Column
    private LocalDateTime createTime;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
    }
}
