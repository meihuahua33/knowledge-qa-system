package com.kqa.service;

import com.kqa.config.RabbitMQConfig;
import com.kqa.model.EmbeddingTaskMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

/**
 * Embedding 任务生产者: 文档解析/切分完成后, 把 Embedding 任务投递到 MQ
 *
 * 面试点:
 * - 为什么用异步? Embedding API 调用可能很慢(大量文本时), 不能让用户等着
 * - 为什么用 MQ 而不是 @Async? MQ 有持久化/重试/死信, @Async 挂了消息就丢了
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingTaskProducer {

    private final RabbitTemplate rabbitTemplate;

    public void send(EmbeddingTaskMessage message) {
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EMBEDDING_QUEUE,
                message
        );
        log.info("Embedding 任务已投递: documentId={}, version={}, chunk数={}",
                message.getDocumentId(),
                message.getVersion(),
                message.getChunks().size());
    }
}
