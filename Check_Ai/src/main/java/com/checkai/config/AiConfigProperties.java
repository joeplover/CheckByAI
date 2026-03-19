package com.checkai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "checkai.ai")
public class AiConfigProperties {
    
    private DeepseekConfig deepseek = new DeepseekConfig();
    private OllamaConfig ollama = new OllamaConfig();
    private MilvusConfig milvus = new MilvusConfig();
    private RagConfig rag = new RagConfig();
    
    @Data
    public static class DeepseekConfig {
        private String apiKey;
        private String baseUrl = "https://api.deepseek.com";
        private String model = "deepseek-chat";
    }
    
    @Data
    public static class OllamaConfig {
        private String baseUrl = "http://localhost:11434";
        private String embeddingModel = "nomic-embed-text";
    }
    
    @Data
    public static class MilvusConfig {
        private String host = "localhost";
        private int port = 19530;
        private String collectionName = "checkai_documents";
        private String database = "default";
    }
    
    @Data
    public static class RagConfig {
        private int maxSegmentSize = 500;
        private int maxOverlapSize = 100;
        private int maxResults = 5;
        private double minScore = 0.7;
    }
}
