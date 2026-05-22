package com.kqa.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "documents", indexes = {
    @Index(name = "idx_category", columnList = "category_id"),
    @Index(name = "idx_status", columnList = "status"),
    @Index(name = "idx_upload_time", columnList = "upload_time")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 500)
    private String title;           // 文档标题

    @Column(nullable = false, length = 50)
    private String fileType;        // PDF / DOCX / TXT

    @Column(nullable = false)
    private Long fileSize;          // 文件大小(字节)

    @Column(length = 500)
    private String filePath;        // 原始文件存储路径

    @Column(columnDefinition = "TEXT")
    private String description;     // 文档描述

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;      // 所属分类

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "PENDING"; // PENDING → PARSING → EMBEDDING → READY → FAILED

    @Column(nullable = false)
    @Builder.Default
    private Integer currentVersion = 1; // 当前版本号

    @Column(nullable = false)
    private LocalDateTime uploadTime;

    @Column
    private LocalDateTime updateTime;

    @PrePersist
    protected void onCreate() {
        uploadTime = LocalDateTime.now();
        updateTime = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
    }
}
