package com.checkai.controller;

import com.checkai.entity.Result;
import com.checkai.service.AiAssistantService;
import com.checkai.service.RagService;
import com.checkai.util.CurrentUserHolder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/ai")
@Tag(name = "AI智能问答", description = "AI智能问答助手接口")
public class AiAssistantController {
    
    @Autowired
    private AiAssistantService aiAssistantService;
    
    @Autowired
    private RagService ragService;
    
    @PostMapping("/chat")
    @Operation(summary = "普通对话", description = "与AI助手进行普通对话")
    public Result<Map<String, Object>> chat(
            @Parameter(description = "用户消息") @RequestBody ChatRequest request) {
        String userId = CurrentUserHolder.getUserId();
        if (userId == null) {
            userId = "anonymous";
        }
        
        String response = aiAssistantService.chat(userId, request.getMessage(), false);
        
        Map<String, Object> result = new HashMap<>();
        result.put("response", response);
        result.put("userId", userId);
        return Result.success(result);
    }
    
    @PostMapping("/chat/rag")
    @Operation(summary = "RAG对话", description = "基于文档的RAG对话")
    public Result<Map<String, Object>> chatWithRag(
            @Parameter(description = "用户消息") @RequestBody ChatRequest request) {
        String userId = CurrentUserHolder.getUserId();
        if (userId == null) {
            userId = "anonymous";
        }
        
        String response = aiAssistantService.chatWithDocuments(userId, request.getMessage());
        
        Map<String, Object> result = new HashMap<>();
        result.put("response", response);
        result.put("userId", userId);
        return Result.success(result);
    }
    
    @PostMapping("/documents/upload")
    @Operation(summary = "上传文档", description = "上传文档用于RAG检索")
    public Result<Map<String, Object>> uploadDocument(
            @Parameter(description = "文档文件") @RequestParam("file") MultipartFile file) {
        String userId = CurrentUserHolder.getUserId();
        if (userId == null) {
            userId = "anonymous";
        }
        
        try {
            int segmentCount = ragService.ingestDocument(file, userId);
            
            Map<String, Object> result = new HashMap<>();
            result.put("filename", file.getOriginalFilename());
            result.put("segments", segmentCount);
            result.put("userId", userId);
            result.put("message", "文档上传成功，已分割为 " + segmentCount + " 个片段");
            return Result.success(result);
        } catch (Exception e) {
            return Result.error("文档上传失败: " + e.getMessage());
        }
    }
    
    @GetMapping("/documents/search")
    @Operation(summary = "检索文档", description = "检索相关文档片段")
    public Result<List<RagService.RetrievalResult>> searchDocuments(
            @Parameter(description = "查询内容") @RequestParam String query,
            @Parameter(description = "返回数量") @RequestParam(defaultValue = "5") int limit) {
        String userId = CurrentUserHolder.getUserId();
        if (userId == null) {
            userId = "anonymous";
        }
        
        List<RagService.RetrievalResult> results = ragService.retrieve(query, userId, limit);
        return Result.success(results);
    }
    
    @GetMapping("/history")
    @Operation(summary = "获取对话历史", description = "获取当前用户的对话历史")
    public Result<List<?>> getHistory() {
        String userId = CurrentUserHolder.getUserId();
        if (userId == null) {
            userId = "anonymous";
        }
        
        List<?> history = aiAssistantService.getHistory(userId);
        return Result.success(history);
    }
    
    @DeleteMapping("/history")
    @Operation(summary = "清除对话历史", description = "清除当前用户的对话历史")
    public Result<Void> clearHistory() {
        String userId = CurrentUserHolder.getUserId();
        if (userId == null) {
            userId = "anonymous";
        }
        
        aiAssistantService.clearMemory(userId);
        return Result.success();
    }
    
    @lombok.Data
    public static class ChatRequest {
        private String message;
    }
}
