package com.kqa.service;

import com.kqa.config.RabbitMQConfig;
import com.kqa.entity.Document;
import com.kqa.entity.DocumentChunk;
import com.kqa.model.EmbeddingTaskMessage;
import com.kqa.repository.DocumentChunkRepository;
import com.kqa.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Embedding 任务消费者: 从 MQ 取消息 → 调 API → 双写 Milvus + ES → 更新状态
 *
 * 面试点:
 * - @RabbitListener 是消息驱动的, 收到一条处理一条
 * - concurrentConsumers=2: 2 个线程并发消费, prefetchCount=1 公平分发
 * - 为什么在此处更新 DB 状态? 异步完成后需要标记 chunk 的可检索状态
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingTaskConsumer {

    private final EmbeddingService embeddingService;
    private final IndexService indexService;
    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository chunkRepository;

    @RabbitListener(queues = RabbitMQConfig.EMBEDDING_QUEUE,
            containerFactory = "rabbitListenerContainerFactory")
    @Transactional
    public void handle(EmbeddingTaskMessage message) {
        log.info("收到 Embedding 任务: documentId={}, version={}, chunks={}",
                message.getDocumentId(), message.getVersion(), message.getChunks().size());

        try {
            // 1. 收集所有 chunk 文本
            List<String> texts = message.getChunks().stream()
                    .map(EmbeddingTaskMessage.ChunkWrapper::getContent)
                    .collect(Collectors.toList());

            // 2. 批量调用 Embedding API
            List<List<Float>> vectors = embeddingService.embedBatch(texts);

            // 3. 写入 Milvus (向量)
            List<Long> chunkIds = message.getChunks().stream()
                    .map(EmbeddingTaskMessage.ChunkWrapper::getChunkId)
                    .collect(Collectors.toList());
            List<Long> milvusIds = indexService.insertVectors(chunkIds, vectors);

            // 4. 写入 ES (文本) + 更新 MySQL 中的 milvusId / esId
            List<DocumentChunk> chunks = chunkRepository.findAllById(chunkIds);
            for (int i = 0; i < chunks.size(); i++) {
                DocumentChunk chunk = chunks.get(i);
                EmbeddingTaskMessage.ChunkWrapper wrapper = message.getChunks().get(i);

                // ES 写入
                String esId = indexService.indexChunk(
                        chunk.getId(),
                        message.getDocumentId(),
                        com.kqa.model.ChunkResult.builder()
                                .index(wrapper.getChunkIndex())
                                .content(wrapper.getContent())
                                .metadata(java.util.Map.of("documentTitle", wrapper.getDocTitle()))
                                .build()
                );

                // 更新 MySQL: 记录 milvusId 和 esId
                chunk.setMilvusId(milvusIds.get(i));
                chunk.setEsId(esId);
            }
            chunkRepository.saveAll(chunks);

            // 5. 更新文档状态为 READY
            Document doc = documentRepository.findById(message.getDocumentId())
                    .orElseThrow(() -> new RuntimeException("文档不存在: " + message.getDocumentId()));
            doc.setStatus("READY");
            documentRepository.save(doc);

            log.info("Embedding 任务处理完成: documentId={}, status=READY", message.getDocumentId());

        } catch (Exception e) {
            log.error("Embedding 任务处理失败: documentId={}", message.getDocumentId(), e);

            // 更新文档状态为 FAILED
            documentRepository.findById(message.getDocumentId()).ifPresent(doc -> {
                doc.setStatus("FAILED");
                documentRepository.save(doc);
            });

            // 抛出异常触发 RabbitMQ 重试机制
            throw new RuntimeException("Embedding 任务失败, 将重试", e);
        }
    }
}
