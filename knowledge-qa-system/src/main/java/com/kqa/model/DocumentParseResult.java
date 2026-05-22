package com.kqa.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文档解析结果: 无论什么格式(PDF/Word/TXT), 最终都转成这个统一结构
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentParseResult {

    private String title;        // 文档标题(从文件名提取)
    private String content;      // 解析出的纯文本(全量)
    private int totalPages;      // 总页数(PDF/Word有, TXT为1)
    private int paragraphCount;  // 段落数
    private long charCount;      // 总字符数
    private String fileType;     // 原始文件类型: pdf / docx / txt
}
