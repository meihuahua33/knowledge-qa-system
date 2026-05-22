package com.kqa.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * RabbitMQ 异步 Embedding 任务消息体
 *
 * 流程: 文档上传 → 解析/切分 → 保存 DB → 发此消息 → Consumer 异步处理
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmbeddingTaskMessage implements Serializable {

    private Long documentId;        // 文档 ID
    private Integer version;         // 版本号
    private List<ChunkWrapper> chunks; // 该版本的所有 chunk

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChunkWrapper implements Serializable {
        private Long chunkId;       // document_chunks 表的主键
        private String content;     // chunk 文本
        private int chunkIndex;     // 序号
        private String docTitle;    // 文档标题(写入 ES metadata)
    }
}
