package com.kqa.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 混合检索的单条结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchResult {

    private Long chunkId;              // MySQL 中 document_chunks 的主键
    private Long documentId;           // 所属文档 ID
    private String content;            // Chunk 文本内容
    private double score;              // RRF 融合后的分数
    private Map<String, Object> metadata; // 元数据: 文档标题、页码、章节
    private String source;             // 来源: "milvus" / "es" / "both"
}
