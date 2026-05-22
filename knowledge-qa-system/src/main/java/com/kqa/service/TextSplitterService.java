package com.kqa.service;

import com.kqa.config.AppProperties;
import com.kqa.model.ChunkResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 文本切分服务: 滑动窗口 + 重叠切分
 *
 * 面试核心:
 * 1. 为什么用滑动窗口而不是按段落切?
 *    - 段落长度不均(一句话 vs 5000字), Embedding 模型有最大 Token 限制
 *    - 固定窗口保证每个 chunk 在模型的"最佳感知范围"内(通常 256-512 tokens)
 *
 * 2. 为什么要 overlap(重叠)?
 *    - 防止关键信息被切在边界上: "甲方应于 / 2024年1月1日前付款"
 *    - overlap 让相邻 chunk 共享一部分文本, 检索时不会漏掉边界信息
 *
 * 3. Token 估算为什么是 charCount / 2?
 *    - 中文: 1 个汉字 ≈ 1-2 个 token (取决于 tokenizer)
 *    - 简单估算取 0.5 token/字符, 偏保守
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TextSplitterService {

    private final AppProperties appProperties;

    /**
     * 将文档全文切分为多个 Chunk
     *
     * @param content  文档全文
     * @param docTitle 文档标题(写入 metadata)
     * @return 切分后的 Chunk 列表
     */
    public List<ChunkResult> split(String content, String docTitle) {
        int maxSize = appProperties.getChunk().getMaxSize();   // 默认 500
        int overlap = appProperties.getChunk().getOverlap();   // 默认 50

        if (content == null || content.isEmpty()) {
            return Collections.emptyList();
        }

        List<ChunkResult> chunks = new ArrayList<>();
        int totalLength = content.length();
        int start = 0;
        int index = 0;

        while (start < totalLength) {
            // 计算当前窗口的结束位置(字符数作为 token 估算)
            int end = Math.min(start + maxSize * 2, totalLength); // *2 因为 token 估算系数

            // 如果不是最后一块, 尝试在句号/换行处断开(更自然的语义边界)
            if (end < totalLength) {
                end = findBestBreakPoint(content, start, end);
            }

            String chunkText = content.substring(start, end).trim();
            if (!chunkText.isEmpty()) {
                int tokenCount = chunkText.length() / 2; // 中文字符 → token 估算

                chunks.add(ChunkResult.builder()
                        .index(index++)
                        .content(chunkText)
                        .tokenCount(tokenCount)
                        .metadata(ChunkResult.MetadataBuilder.build(docTitle, 0, null))
                        .build());
            }

            // 下一个窗口: 从 end - overlap 处开始
            start = end - overlap * 2;
            if (start >= totalLength) break;
            // 防止死循环(当 overlap >= chunk 大小时)
            if (start <= 0) start = end;
        }

        log.info("文本切分完成: 文档={}, 总字符={}, 切分块数={}, chunk大小={}, 重叠={}",
                docTitle, totalLength, chunks.size(), maxSize, overlap);

        return chunks;
    }

    /**
     * 在 [start, end] 范围内找最佳断点: 优先在句号/问号/感叹号/换行处断开
     */
    private int findBestBreakPoint(String content, int start, int end) {
        // 在 end 左边 100 字符范围内搜索断点
        int searchStart = Math.max(start, end - 200);
        String segment = content.substring(searchStart, end);

        // 按优先级找断点: 双换行 > 单换行 > 句号 > 分号 > 逗号
        char[] breakChars = {'\n', '。', '！', '？', '；', '，'};

        for (char c : breakChars) {
            int pos = segment.lastIndexOf(c);
            if (pos > 0) {
                int breakPoint = searchStart + pos + 1; // 断在标点之后
                if (breakPoint > start && breakPoint <= end) {
                    return breakPoint;
                }
            }
        }

        return end; // 找不到合适的断点就用原 end
    }
}
