package com.kqa.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 文本切分后的单个 Chunk
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChunkResult {

    private int index;           // chunk 序号(从0开始)
    private String content;      // chunk 文本内容
    private int tokenCount;      // 估算的 Token 数
    private Map<String, Object> metadata; // 元数据: 章节标题、页码、文档标题等

    // 方便构建 metadata
    public static class MetadataBuilder {
        public static Map<String, Object> build(String docTitle, int pageNum, String sectionTitle) {
            return Map.of(
                "documentTitle", docTitle,
                "page", pageNum,
                "section", sectionTitle != null ? sectionTitle : ""
            );
        }
    }
}
