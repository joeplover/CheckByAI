package com.checkai.service;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.checkai.config.AiConfigProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class RagService {
    
    private static final Logger logger = LoggerFactory.getLogger(RagService.class);
    
    @Autowired
    private EmbeddingModel embeddingModel;
    
    @Autowired
    private EmbeddingStore<TextSegment> embeddingStore;
    
    @Autowired
    private DocumentParserService documentParserService;
    
    @Autowired
    private AiConfigProperties aiConfig;
    
    public int ingestDocument(MultipartFile file, String userId) throws Exception {
        List<TextSegment> segments = documentParserService.parseDocument(file, userId);
        return ingestSegments(segments, userId);
    }
    
    public int ingestDocument(java.io.File file, String userId) throws Exception {
        List<TextSegment> segments = documentParserService.parseDocument(file, userId);
        return ingestSegments(segments, userId);
    }
    
    private int ingestSegments(List<TextSegment> segments, String userId) {
        logger.info("开始向量化并存储文档片段: segments={}, userId={}", segments.size(), userId);
        
        List<Embedding> embeddings = embeddingModel.embedAll(segments).content();
        
        for (int i = 0; i < segments.size(); i++) {
            TextSegment segment = segments.get(i);
            Embedding embedding = embeddings.get(i);
            embeddingStore.add(embedding, segment);
        }
        
        logger.info("文档向量化存储完成: segments={}, userId={}", segments.size(), userId);
        return segments.size();
    }
    
    public List<RetrievalResult> retrieve(String query, String userId) {
        return retrieve(query, userId, aiConfig.getRag().getMaxResults());
    }
    
    public List<RetrievalResult> retrieve(String query, String userId, int maxResults) {
        logger.info("检索相关文档: query={}, userId={}, maxResults={}", query, userId, maxResults);
        
        Embedding queryEmbedding = embeddingModel.embed(query).content();
        
        EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
            .queryEmbedding(queryEmbedding)
            .maxResults(maxResults)
            .minScore(aiConfig.getRag().getMinScore())
            .build();
        
        EmbeddingSearchResult<TextSegment> searchResult = embeddingStore.search(searchRequest);
        
        List<RetrievalResult> results = searchResult.matches().stream()
            .filter(match -> {
                Metadata meta = match.embedded().metadata();
                String segmentUserId = meta.getString("userId");
                return userId.equals(segmentUserId);
            })
            .map(match -> {
                Metadata meta = match.embedded().metadata();
                String filename = meta.getString("filename");
                String documentId = meta.getString("documentId");
                return new RetrievalResult(
                    match.embedded().text(),
                    match.score(),
                    filename != null ? filename : "unknown",
                    documentId
                );
            })
            .collect(Collectors.toList());
        
        logger.info("检索完成: results={}", results.size());
        return results;
    }
    
    public String buildContext(List<RetrievalResult> results) {
        if (results.isEmpty()) {
            return "";
        }
        
        StringBuilder context = new StringBuilder();
        context.append("以下是相关的参考文档内容：\n\n");
        
        for (int i = 0; i < results.size(); i++) {
            RetrievalResult result = results.get(i);
            context.append(String.format("[文档%d] (来源: %s, 相关度: %.2f)\n", 
                i + 1, result.getFilename(), result.getScore()));
            context.append(result.getContent()).append("\n\n");
        }
        
        return context.toString();
    }
    
    @lombok.Data
    @lombok.AllArgsConstructor
    public static class RetrievalResult {
        private String content;
        private double score;
        private String filename;
        private String documentId;
    }
}
