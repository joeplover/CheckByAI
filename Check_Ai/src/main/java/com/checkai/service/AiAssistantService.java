package com.checkai.service;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.input.PromptTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.checkai.config.AiConfigProperties;
import com.checkai.util.RedisUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AiAssistantService {
    
    private static final Logger logger = LoggerFactory.getLogger(AiAssistantService.class);
    
    private static final String CHAT_MEMORY_PREFIX = "CHAT_MEMORY:";
    private static final int MAX_MEMORY_MESSAGES = 20;
    
    @Autowired
    private ChatLanguageModel chatModel;
    
    @Autowired
    private RagService ragService;
    
    @Autowired
    private AiConfigProperties aiConfig;
    
    @Autowired
    private RedisUtil redisUtil;
    
    private final Map<String, ChatMemory> memoryCache = new ConcurrentHashMap<>();
    
    private static final String SYSTEM_PROMPT = """
        你是一个智能问答助手，具有以下能力：
        1. 能够根据用户上传的文档内容回答问题
        2. 能够进行自然语言对话
        3. 能够记忆之前的对话内容
        
        请根据提供的上下文信息回答用户问题。如果上下文中没有相关信息，请诚实告知用户。
        回答时请保持专业、准确、友好的态度。
        """;
    
    private static final String RAG_PROMPT_TEMPLATE = """
        {{system_prompt}}
        
        {{context}}
        
        用户问题：{{query}}
        
        请基于以上信息回答用户的问题：
        """;
    
    public String chat(String userId, String userMessage) {
        return chat(userId, userMessage, false);
    }
    
    public String chat(String userId, String userMessage, boolean useRag) {
        logger.info("处理对话: userId={}, message={}, useRag={}", userId, userMessage, useRag);
        
        ChatMemory memory = getOrCreateMemory(userId);
        
        String context = "";
        if (useRag) {
            List<RagService.RetrievalResult> results = ragService.retrieve(userMessage, userId);
            context = ragService.buildContext(results);
        }
        
        String prompt;
        if (useRag && !context.isEmpty()) {
            prompt = buildRagPrompt(userMessage, context);
        } else {
            prompt = userMessage;
        }
        
        memory.add(SystemMessage.from(SYSTEM_PROMPT));
        memory.add(UserMessage.from(prompt));
        
        try {
            AiMessage response = chatModel.generate(memory.messages()).content();
            memory.add(response);
            
            saveMemoryToRedis(userId, memory);
            
            logger.info("对话完成: userId={}", userId);
            return response.text();
        } catch (Exception e) {
            logger.error("对话处理失败: userId={}, error={}", userId, e.getMessage(), e);
            throw new RuntimeException("对话处理失败: " + e.getMessage());
        }
    }
    
    public String chatWithDocuments(String userId, String userMessage) {
        return chat(userId, userMessage, true);
    }
    
    private String buildRagPrompt(String query, String context) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("system_prompt", SYSTEM_PROMPT);
        variables.put("context", context);
        variables.put("query", query);
        
        PromptTemplate template = PromptTemplate.from(RAG_PROMPT_TEMPLATE);
        Prompt prompt = template.apply(variables);
        return prompt.text();
    }
    
    private ChatMemory getOrCreateMemory(String userId) {
        ChatMemory memory = memoryCache.get(userId);
        if (memory == null) {
            memory = loadMemoryFromRedis(userId);
            if (memory == null) {
                memory = MessageWindowChatMemory.builder()
                    .maxMessages(MAX_MEMORY_MESSAGES)
                    .build();
            }
            memoryCache.put(userId, memory);
        }
        return memory;
    }
    
    private ChatMemory loadMemoryFromRedis(String userId) {
        try {
            String key = CHAT_MEMORY_PREFIX + userId;
            Object cached = redisUtil.get(key);
            if (cached != null) {
                @SuppressWarnings("unchecked")
                List<Map<String, String>> messages = (List<Map<String, String>>) cached;
                ChatMemory memory = MessageWindowChatMemory.builder()
                    .maxMessages(MAX_MEMORY_MESSAGES)
                    .build();
                
                for (Map<String, String> msg : messages) {
                    String type = msg.get("type");
                    String content = msg.get("content");
                    switch (type) {
                        case "system":
                            memory.add(SystemMessage.from(content));
                            break;
                        case "user":
                            memory.add(UserMessage.from(content));
                            break;
                        case "ai":
                            memory.add(AiMessage.from(content));
                            break;
                    }
                }
                return memory;
            }
        } catch (Exception e) {
            logger.warn("加载对话记忆失败: userId={}, error={}", userId, e.getMessage());
        }
        return null;
    }
    
    private void saveMemoryToRedis(String userId, ChatMemory memory) {
        try {
            List<Map<String, String>> messages = new ArrayList<>();
            for (ChatMessage msg : memory.messages()) {
                Map<String, String> messageMap = new HashMap<>();
                if (msg instanceof SystemMessage) {
                    messageMap.put("type", "system");
                    messageMap.put("content", ((SystemMessage) msg).text());
                } else if (msg instanceof UserMessage) {
                    messageMap.put("type", "user");
                    messageMap.put("content", ((UserMessage) msg).singleText());
                } else if (msg instanceof AiMessage) {
                    messageMap.put("type", "ai");
                    messageMap.put("content", ((AiMessage) msg).text());
                }
                messages.add(messageMap);
            }
            
            String key = CHAT_MEMORY_PREFIX + userId;
            redisUtil.set(key, messages);
        } catch (Exception e) {
            logger.warn("保存对话记忆失败: userId={}, error={}", userId, e.getMessage());
        }
    }
    
    public void clearMemory(String userId) {
        memoryCache.remove(userId);
        String key = CHAT_MEMORY_PREFIX + userId;
        redisUtil.delete(key);
        logger.info("清除对话记忆: userId={}", userId);
    }
    
    public List<ChatMessage> getHistory(String userId) {
        ChatMemory memory = getOrCreateMemory(userId);
        return new ArrayList<>(memory.messages());
    }
}
