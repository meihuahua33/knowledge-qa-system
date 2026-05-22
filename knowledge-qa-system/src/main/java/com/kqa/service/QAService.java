package com.kqa.service;

import com.kqa.model.SearchResult;
import com.kqa.repository.DocumentChunkRepository;
import com.kqa.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * 问答编排服务: 检索 → Prompt 组装 → LLM 生成 → 返回
 *
 * 面试点:
 * - Prompt Engineering: 如何把检索结果拼成有效的 Prompt
 * - RAG 的"引用溯源": 告诉用户回答来自哪个文档
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QAService {

    private final SearchService searchService;
    private final LLMService llmService;
    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository chunkRepository;

    /**
     * 检索相关文档片段
     */
    public List<SearchResult> search(String question) {
        List<SearchResult> results = searchService.hybridSearch(question);

        // 补充每个结果的 content 和 metadata(向量检索返回的结果可能缺失)
        for (SearchResult r : results) {
            if (r.getContent() == null && r.getChunkId() != null) {
                chunkRepository.findById(r.getChunkId()).ifPresent(chunk -> {
                    r.setContent(chunk.getContent());
                    r.setDocumentId(chunk.getDocumentId());
                });
            }
            if (r.getDocumentId() != null) {
                documentRepository.findById(r.getDocumentId()).ifPresent(doc -> {
                    r.setMetadata(Map.of(
                            "documentTitle", doc.getTitle(),
                            "source", "milvus".equals(r.getSource()) ? "向量匹配" : "关键词匹配"
                    ));
                });
            }
        }

        log.info("检索完成: 问题='{}', 命中 {} 条", question, results.size());
        return results;
    }

    /**
     * 流式问答
     *
     * @param question 用户问题
     * @param onChunk  流式回调(每段文本)
     * @return 完整的引用来源列表(用于最后展示)
     */
    public List<Map<String, Object>> askStreaming(String question, Consumer<String> onChunk) {

        // 1. 检索相关文档
        List<SearchResult> searchResults = search(question);

        // 2. 组装 System Prompt
        String systemPrompt = buildSystemPrompt();

        // 3. 组装 User Prompt(含检索到的上下文)
        String userPrompt = buildUserPrompt(question, searchResults);

        // 4. 调用 DeepSeek, 流式回调每个 token
        llmService.chatStream(systemPrompt, userPrompt, onChunk);

        // 5. 返回引用列表(供前端展示"参考来源")
        return searchResults.stream()
                .map(r -> Map.<String, Object>of(
                        "content", r.getContent() != null ? truncate(r.getContent(), 200) : "",
                        "metadata", r.getMetadata() != null ? r.getMetadata() : Map.of(),
                        "score", r.getScore()
                ))
                .collect(Collectors.toList());
    }

    // ==================== Prompt 模板 ====================

    /**
     * System Prompt: 设定 AI 的"人设"和行为边界
     */
    private String buildSystemPrompt() {
        return """
                你是一个专业的企业知识库助手，专门帮助用户解答基于企业内部文档的问题。

                请遵循以下规则:
                1. 只根据提供的文档内容作答，不要编造信息或依赖外部知识
                2. 如果文档中没有相关信息，请明确告知用户"文档中未找到相关信息"
                3. 回答要简洁清晰，尽量用分点列出的方式呈现
                4. 引用的内容请标注来自哪个文档
                5. 使用中文回答
                """;
    }

    /**
     * User Prompt: 包含检索到的上下文 + 用户问题
     */
    private String buildUserPrompt(String question, List<SearchResult> results) {
        StringBuilder sb = new StringBuilder();
        sb.append("请根据以下文档内容回答用户的问题：\n\n");
        sb.append("【文档内容】\n");

        for (int i = 0; i < results.size(); i++) {
            SearchResult r = results.get(i);
            String docTitle = "未知来源";
            if (r.getMetadata() != null && r.getMetadata().get("documentTitle") != null) {
                docTitle = r.getMetadata().get("documentTitle").toString();
            }
            sb.append("---\n");
            sb.append("文档片段 ").append(i + 1).append(" (来源: ").append(docTitle).append(")\n");
            sb.append(r.getContent()).append("\n");
        }

        sb.append("\n【用户问题】\n");
        sb.append(question).append("\n");

        return sb.toString();
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }
}
