package com.kqa.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private Chunk chunk = new Chunk();
    private Retrieval retrieval = new Retrieval();
    private Conversation conversation = new Conversation();
    private Embedding embedding = new Embedding();
    private Llm llm = new Llm();

    @Data
    public static class Chunk {
        private int maxSize = 500;
        private int overlap = 50;
    }

    @Data
    public static class Retrieval {
        private int topK = 5;
        private int vectorCandidates = 20;
        private int keywordCandidates = 20;
        private int rrfK = 60;
    }

    @Data
    public static class Conversation {
        private int maxHistory = 10;
        private int sessionTtl = 3600;
    }

    @Data
    public static class Embedding {
        private String apiKey = "your-api-key-here";
        private String baseUrl = "https://api.openai.com";
        private String model = "text-embedding-3-small";
    }

    @Data
    public static class Llm {
        private String apiKey = "your-api-key-here";
        private String baseUrl = "https://api.deepseek.com";
        private String model = "deepseek-chat";
    }
}
