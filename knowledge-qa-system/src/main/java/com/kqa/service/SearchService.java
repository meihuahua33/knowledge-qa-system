package com.kqa.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.kqa.config.AppProperties;
import com.kqa.model.SearchResult;
import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.DataType;
import io.milvus.grpc.SearchResults;
import io.milvus.param.MetricType;
import io.milvus.param.R;
import io.milvus.param.dml.SearchParam;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 混合检索服务: ES 关键词检索 + Milvus 向量检索 → RRF 融合排序
 *
 * 面试核心——RRF (Reciprocal Rank Fusion):
 *   公式: RRF_score(d) = Σ 1 / (k + rank_i)
 *     k = 60 (修正参数, 防止低排名被过度打压)
 *     rank_i = 文档在第 i 个检索结果列表中的排名(从 1 开始)
 *
 *   举例: 某文档在向量检索排第2、在关键词检索排第5
 *     RRF = 1/(60+2) + 1/(60+5) = 0.01613 + 0.01538 = 0.0315
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SearchService {

    private final MilvusServiceClient milvusClient;
    private final ElasticsearchClient esClient;
    private final EmbeddingService embeddingService;
    private final AppProperties appProperties;

    private static final String MILVUS_COLLECTION = "knowledge_chunks";
    private static final String ES_INDEX = "knowledge_chunks";

    /**
     * 混合检索入口
     *
     * @param question 用户原始问题
     * @return RRF 融合排序后的 Top-K 结果
     */
    public List<SearchResult> hybridSearch(String question) {
        AppProperties.Retrieval retrieval = appProperties.getRetrieval();
        int topK = retrieval.getTopK();                        // 最终返回数, 默认 5
        int vectorCandidates = retrieval.getVectorCandidates(); // 向量候选数, 默认 20
        int keywordCandidates = retrieval.getKeywordCandidates(); // 关键词候选数, 默认 20
        int rrfK = retrieval.getRrfK();                        // RRF 参数 k, 默认 60

        // 1. 问题向量化
        List<Float> questionVector = embeddingService.embed(question);

        // 2. Milvus 向量检索
        List<SearchResult> vectorResults = vectorSearch(questionVector, vectorCandidates);

        // 3. ES 关键词检索
        List<SearchResult> keywordResults = keywordSearch(question, keywordCandidates);

        // 4. RRF 融合排序 + 截断 Top-K
        List<SearchResult> merged = rrfFusion(vectorResults, keywordResults, rrfK, topK);

        log.info("混合检索完成: 问题='{}', 向量匹配={}, 关键词匹配={}, 融合后={}",
                question, vectorResults.size(), keywordResults.size(), merged.size());

        return merged;
    }

    // ==================== Milvus 向量检索 ====================

    private List<SearchResult> vectorSearch(List<Float> questionVector, int topK) {
        try {
            SearchParam searchParam = SearchParam.newBuilder()
                    .withCollectionName(MILVUS_COLLECTION)
                    .withMetricType(MetricType.COSINE)
                    .withTopK(topK)
                    .withVectorFieldName("embedding")
                    .withFloatVectors(Collections.singletonList(questionVector))
                    .build();

            R<SearchResults> response = milvusClient.search(searchParam);

            // SearchResults 在 SDK 2.4.x 中通过 getFieldsData 等方式获取数据
            // 具体 API 以实际运行的 SDK 版本为准, 编译阶段不做强类型解析
            log.debug("Milvus 向量检索已发起, topK={}", topK);
            // TODO: 等 Docker 启动后根据实际 SDK API 完善结果解析

            return Collections.emptyList();
        } catch (Exception e) {
            log.warn("Milvus 向量检索暂不可用(Docker未启动或API版本差异): {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    // ==================== ES 关键词检索 ====================

    private List<SearchResult> keywordSearch(String question, int topK) {
        try {
            SearchResponse<Map> response = esClient.search(
                    sr -> sr.index(ES_INDEX)
                            .size(topK)
                            .query(q -> q
                                    .multiMatch(mm -> mm
                                            .fields("title", "content")     // 在标题和内容中搜索
                                            .query(question)                // 搜索关键词
                                            .analyzer("ik_smart")           // 搜索时粗粒度分词
                                    )
                            ),
                    Map.class
            );

            List<SearchResult> results = new ArrayList<>();
            for (Hit<Map> hit : response.hits().hits()) {
                Map<String, Object> source = hit.source();
                if (source == null) continue;

                results.add(SearchResult.builder()
                        .chunkId(source.get("chunkId") instanceof Integer ?
                                ((Integer) source.get("chunkId")).longValue() :
                                (Long) source.get("chunkId"))
                        .documentId(source.get("documentId") instanceof Integer ?
                                ((Integer) source.get("documentId")).longValue() :
                                (Long) source.get("documentId"))
                        .content((String) source.get("content"))
                        .score(hit.score() != null ? hit.score() : 0.0)    // ES _score
                        .source("es")
                        .build());
            }

            return results;

        } catch (Exception e) {
            log.error("ES 关键词检索失败", e);
            return Collections.emptyList();
        }
    }

    // ==================== RRF 融合排序 ====================

    /**
     * RRF (Reciprocal Rank Fusion): 把两个检索结果列表合并为统一排序
     *
     * 算法步骤:
     *   1. 给两个列表中的每条结果按排名赋值: score = 1 / (k + rank)
     *   2. 同一 chunkId 的分数累加(跨两个列表)
     *   3. 按累计分数降序排列, 截取 Top-K
     *
     * @param vectorResults   向量检索结果
     * @param keywordResults  关键词检索结果
     * @param k               RRF 修正参数(默认 60)
     * @param topK            最终返回条数
     */
    private List<SearchResult> rrfFusion(
            List<SearchResult> vectorResults,
            List<SearchResult> keywordResults,
            int k, int topK) {

        // chunkId → 累加的 RRF 分数
        Map<Long, Double> scoreMap = new LinkedHashMap<>();
        // chunkId → SearchResult (保存 metadata、content 等完整信息)
        Map<Long, SearchResult> resultMap = new LinkedHashMap<>();

        // 给向量检索结果加 RRF 分
        for (int i = 0; i < vectorResults.size(); i++) {
            SearchResult r = vectorResults.get(i);
            double rrfScore = 1.0 / (k + i + 1);  // i+1 = 排名(从1开始)
            scoreMap.merge(r.getChunkId(), rrfScore, Double::sum);
            //         ↑ merge: 如果 key 已存在, 用 Double::sum 累加
            r.setScore(rrfScore);
            resultMap.putIfAbsent(r.getChunkId(), r);
            //        ↑ putIfAbsent: 只在 key 不存在时放入(保留第一次的来源标注)
        }

        // 给关键词检索结果加 RRF 分
        for (int i = 0; i < keywordResults.size(); i++) {
            SearchResult r = keywordResults.get(i);
            double rrfScore = 1.0 / (k + i + 1);
            scoreMap.merge(r.getChunkId(), rrfScore, Double::sum);
            // 如果该 chunk 在两个列表里都出现了, source 改为 "both"
            if (resultMap.containsKey(r.getChunkId())) {
                resultMap.get(r.getChunkId()).setSource("both");
            } else {
                r.setScore(rrfScore);
                resultMap.put(r.getChunkId(), r);
            }
            // 补充 content 和 documentId(向量检索结果中这些字段为空)
            SearchResult existing = resultMap.get(r.getChunkId());
            if (existing.getContent() == null && r.getContent() != null) {
                existing.setContent(r.getContent());
            }
            if (existing.getDocumentId() == null && r.getDocumentId() != null) {
                existing.setDocumentId(r.getDocumentId());
            }
        }

        // 按 RRF 分数降序排序, 截 Top-K
        return scoreMap.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .limit(topK)
                .map(entry -> {
                    SearchResult r = resultMap.get(entry.getKey());
                    r.setScore(entry.getValue());  // 更新为融合后的总分
                    return r;
                })
                .collect(Collectors.toList());
    }
}
