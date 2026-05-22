package com.kqa.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "document_versions", indexes = {
    @Index(name = "idx_doc_id", columnList = "document_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "document_id", nullable = false)
    private Long documentId;

    @Column(nullable = false)
    private Integer versionNumber;  // 版本号: 1, 2, 3...

    @Column(length = 500)
    private String changeLog;       // 变更说明

    @Column(nullable = false)
    private Integer chunkCount;     // 该版本包含的 chunk 数量

    @Column(length = 64)
    private String contentHash;     // 全文哈希, 用于增量对比

    @Column(nullable = false)
    private LocalDateTime createTime;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
    }
}
