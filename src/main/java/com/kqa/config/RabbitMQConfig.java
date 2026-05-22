package com.kqa.config;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String EMBEDDING_QUEUE = "embedding.task.queue";

    @Bean
    public Queue embeddingTaskQueue() {
        return new Queue(EMBEDDING_QUEUE, true); // durable=true, 消息持久化
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setConcurrentConsumers(2);        // 并发消费者数
        factory.setPrefetchCount(1);              // 每次只取1条, 公平分发
        return factory;
    }
}
