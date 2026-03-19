package com.checkai.config;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.milvus.MilvusEmbeddingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LangChain4jConfig {
    
    private static final Logger logger = LoggerFactory.getLogger(LangChain4jConfig.class);
    
    @Autowired
    private AiConfigProperties aiConfig;
    
    @Bean
    public ChatLanguageModel chatLanguageModel() {
        logger.info("初始化DeepSeek Chat模型: baseUrl={}, model={}", 
            aiConfig.getDeepseek().getBaseUrl(), 
            aiConfig.getDeepseek().getModel());
        
        return OpenAiChatModel.builder()
            .baseUrl(aiConfig.getDeepseek().getBaseUrl() + "/v1")
            .apiKey(aiConfig.getDeepseek().getApiKey())
            .modelName(aiConfig.getDeepseek().getModel())
            .temperature(0.7)
            .maxTokens(4096)
            .build();
    }
    
    @Bean
    public EmbeddingModel embeddingModel() {
        String ollamaUrl = aiConfig.getOllama().getBaseUrl();
        String embeddingModelName = aiConfig.getOllama().getEmbeddingModel();
        
        logger.info("初始化Ollama Embedding模型: url={}, model={}", ollamaUrl, embeddingModelName);
        
        return OllamaEmbeddingModel.builder()
            .baseUrl(ollamaUrl)
            .modelName(embeddingModelName)
            .build();
    }
    
    @Bean
    public EmbeddingStore<TextSegment> embeddingStore() {
        String host = aiConfig.getMilvus().getHost();
        int port = aiConfig.getMilvus().getPort();
        String collectionName = aiConfig.getMilvus().getCollectionName();
        String database = aiConfig.getMilvus().getDatabase();
        
        logger.info("初始化Milvus向量存储: host={}, port={}, collection={}, database={}", 
            host, port, collectionName, database);
        
        return MilvusEmbeddingStore.builder()
            .host(host)
            .port(port)
            .collectionName(collectionName)
            .databaseName(database)
            .dimension(768)
            .build();
    }
}
