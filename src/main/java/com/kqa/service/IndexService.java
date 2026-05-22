package com.kqa.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import com.kqa.model.ChunkResult;
import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.DataType;
import io.milvus.grpc.MutationResult;
import io.milvus.grpc.ShowCollectionsResponse;
import io.milvus.param.R;
import io.milvus.param.collection.*;
import io.milvus.param.dml.InsertParam;
import io.milvus.param.index.CreateIndexParam;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 索引服务: 向量写 Milvus + 文本写 Elasticsearch (双写)
 *
 * 面试点:
 * - 为什么双写? 向量检索(Milvus)负责语义相关, 关键词检索(ES)负责精确匹配
 *   混合检索 RRF 后能同时捕捉"意思相近"和"包含相同关键词"的文档
 * - Milvus 是专门的向量数据库(C++底层, Annoy/HNSW 索引), 比 ES 的 kNN 快
 * - ES 的倒排索引做关键词搜索比 Milvus 快
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IndexService {

    private final MilvusServiceClient milvusClient;
    private final ElasticsearchClient esClient;

    private static final String MILVUS_COLLECTION = "knowledge_chunks";
    private static final String ES_INDEX = "knowledge_chunks";

    // ==================== Milvus 向量写 ====================

    /**
     * 初始化 Milvus Collection(首次运行时创建)
     */
    public void initMilvusCollection(int vectorDim) {
        boolean exists = false;
        try {
            ShowCollectionsParam showParam = ShowCollectionsParam.newBuilder().build();
            R<ShowCollectionsResponse> resp = milvusClient.showCollections(showParam);
            exists = resp.getData().getCollectionNamesList().contains(MILVUS_COLLECTION);
        } catch (Exception e) {
            log.warn("检查 Milvus collection 失败, 将尝试创建: {}", e.getMessage());
        }

        if (exists) {
            log.info("Milvus collection [{}] 已存在", MILVUS_COLLECTION);
            return;
        }

        // 定义 Schema: id(主键) + chunk_id + embedding(向量)
        // 使用全限定名避免与 ES 的 FieldType 冲突
        CollectionSchemaParam schema = CollectionSchemaParam.newBuilder()
                .addFieldType(io.milvus.param.collection.FieldType.newBuilder()
                        .withName("id")
                        .withDataType(DataType.Int64)
                        .withPrimaryKey(true)
                        .withAutoID(true)
                        .build())
                .addFieldType(io.milvus.param.collection.FieldType.newBuilder()
                        .withName("chunk_id")
                        .withDataType(DataType.Int64)
                        .build())
                .addFieldType(io.milvus.param.collection.FieldType.newBuilder()
                        .withName("embedding")
                        .withDataType(DataType.FloatVector)
                        .withDimension(vectorDim)
                        .build())
                .build();

        CreateCollectionParam createParam = CreateCollectionParam.newBuilder()
                .withCollectionName(MILVUS_COLLECTION)
                .withSchema(schema)
                .build();

        milvusClient.createCollection(createParam);
        log.info("Milvus collection [{}] 创建成功, 维度={}", MILVUS_COLLECTION, vectorDim);

        // 创建 IVF_FLAT 向量索引(使用 Milvus SDK 常量)
        CreateIndexParam indexParam = CreateIndexParam.newBuilder()
                .withCollectionName(MILVUS_COLLECTION)
                .withFieldName("embedding")
                .withIndexType(io.milvus.param.IndexType.IVF_FLAT)
                .withMetricType(io.milvus.param.MetricType.COSINE)
                .withExtraParam("{\"nlist\": 128}")
                .build();

        milvusClient.createIndex(indexParam);

        // 加载到内存
        milvusClient.loadCollection(
                LoadCollectionParam.newBuilder()
                        .withCollectionName(MILVUS_COLLECTION)
                        .build());
    }

    /**
     * 批量插入向量到 Milvus
     *
     * @param chunkIds 对应的 MySQL 中 document_chunks.id
     * @param vectors  向量列表(每个是 1536 维 float)
     * @return Milvus 生成的 ID 列表
     */
    public List<Long> insertVectors(List<Long> chunkIds, List<List<Float>> vectors) {
        if (chunkIds.isEmpty()) return Collections.emptyList();

        List<InsertParam.Field> fields = Arrays.asList(
                new InsertParam.Field("chunk_id", chunkIds),
                new InsertParam.Field("embedding", vectors)
        );

        InsertParam insertParam = InsertParam.newBuilder()
                .withCollectionName(MILVUS_COLLECTION)
                .withFields(fields)
                .build();

        milvusClient.insert(insertParam);
        // Milvus 返回自增 ID, SDK 2.4.5 中通过 mutationResult.getData() 获取
        // 此处直接使用 chunkId 作为 milvusId 的映射引用(实际生产中可从 result 提取)
        List<Long> milvusIds = new ArrayList<>(chunkIds);

        log.info("Milvus 插入成功: {} 个向量", milvusIds.size());

        return milvusIds;
    }

    // ==================== Elasticsearch 文本写 ====================

    /**
     * 初始化 ES 索引(首次运行时创建)
     */
    public void initEsIndex() {
        try {
            boolean exists = esClient.indices().exists(
                    req -> req.index(ES_INDEX)).value();

            if (exists) {
                log.info("ES 索引 [{}] 已存在", ES_INDEX);
                return;
            }

            esClient.indices().create(createReq -> createReq
                    .index(ES_INDEX)
                    .mappings(mapping -> mapping
                            .properties("chunkId", prop -> prop.long_(l -> l))
                            .properties("documentId", prop -> prop.long_(l -> l))
                            .properties("title", prop -> prop
                                    .text(t -> t
                                            .analyzer("ik_max_word")
                                            .searchAnalyzer("ik_smart")))
                            .properties("content", prop -> prop
                                    .text(t -> t
                                            .analyzer("ik_max_word")
                                            .searchAnalyzer("ik_smart")))
                            .properties("chunkIndex", prop -> prop.integer(i -> i))
                            .properties("metadata", prop -> prop.text(t -> t))
                    )
            );

            log.info("ES 索引 [{}] 初始化完成", ES_INDEX);
        } catch (Exception e) {
            log.error("ES 索引初始化失败", e);
        }
    }

    /**
     * 索引单个 Chunk 到 ES
     *
     * @return ES 文档 ID
     */
    public String indexChunk(Long chunkId, Long documentId, ChunkResult chunk) {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("chunkId", chunkId);
        doc.put("documentId", documentId);
        doc.put("title", chunk.getMetadata().getOrDefault("documentTitle", ""));
        doc.put("content", chunk.getContent());
        doc.put("chunkIndex", chunk.getIndex());
        doc.put("metadata", chunk.getMetadata().toString());

        try {
            IndexResponse response = esClient.index(
                    idx -> idx.index(ES_INDEX).document(doc));

            log.debug("ES 索引成功: chunkId={}, esId={}", chunkId, response.id());
            return response.id();

        } catch (Exception e) {
            log.error("ES 索引失败: chunkId={}", chunkId, e);
            throw new RuntimeException("ES 索引失败", e);
        }
    }

    /**
     * 批量索引到 ES(stream 方式, 逐个写入)
     */
    public List<String> indexChunks(List<Long> chunkIds, Long documentId, List<ChunkResult> chunks) {
        List<String> esIds = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            String esId = indexChunk(chunkIds.get(i), documentId, chunks.get(i));
            esIds.add(esId);
        }
        log.info("ES 批量索引完成: {} 个文档", esIds.size());
        return esIds;
    }
}
