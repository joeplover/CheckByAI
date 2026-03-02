package com.checkai.service;

import com.checkai.entity.CallbackData;
import com.checkai.entity.Task;
import com.checkai.mapper.CallbackDataMapper;
import com.checkai.mapper.TaskMapper;
import com.checkai.util.ShortIdUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.nio.charset.StandardCharsets;

@Service
public class CallbackService {

    private static final Logger logger = LoggerFactory.getLogger(CallbackService.class);

    @Autowired
    private CallbackDataMapper callbackDataMapper;

    @Autowired
    private TaskMapper taskMapper;

    @Autowired(required = false)
    private CacheManager cacheManager;

    @Autowired
    private IdempotencyService idempotencyService;

    private static final Pattern BATCH_TASK_ID_PATTERN = Pattern.compile("^(.*?)_batch_(\\d+)$");

    public void processCallback(String taskId, String data) {
        try {
            logger.info("收到回调数据 - taskId={}", taskId);
            
            if (idempotencyService.isCallbackProcessed(taskId)) {
                logger.warn("回调已处理，跳过 - taskId={}", taskId);
                return;
            }
            
            String originalTaskId = taskId;
            Integer batchNumber = null;

            Matcher matcher = BATCH_TASK_ID_PATTERN.matcher(taskId);
            if (matcher.matches()) {
                originalTaskId = matcher.group(1);
                batchNumber = Integer.parseInt(matcher.group(2));
            }
            
            Task task = taskMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Task>().eq("task_id", originalTaskId));
            
            if (task == null) {
                logger.warn("taskId无效，不插入数据库 - originalTaskId={}", originalTaskId);
                return;
            }
            
            logger.info("taskId有效，处理回调数据 - originalTaskId={}", originalTaskId);

            boolean isFailure = false;
            try {
                if (data != null && (data.contains("error") || data.contains("failed") || data.contains("FAILURE"))) {
                    isFailure = true;
                    logger.warn("检测到失败回调 - originalTaskId={}, batchNumber={}", originalTaskId, batchNumber);
                }
            } catch (Exception e) {
                logger.error("解析回调数据失败: {}", e.getMessage());
            }

            String userId = task.getUserId();

            if (isFailure) {
                Task updateTask = new Task();
                updateTask.setStatus("FAILED");
                updateTask.setUpdateTime(new Date());
                updateTask.setProgress(task.getTotalProgress());
                taskMapper.update(updateTask, new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Task>().eq("task_id", originalTaskId));
                
                CallbackData callbackData = new CallbackData();
                callbackData.setId(ShortIdUtil.generateShortId());
                callbackData.setTaskId(originalTaskId);
                callbackData.setOriginalTaskId(originalTaskId);
                callbackData.setUserId(userId);
                String failureData = "[FAILURE]\n" + data;
                callbackData.setData(Base64.getEncoder().encodeToString(failureData.getBytes(StandardCharsets.UTF_8)));
                callbackData.setReceiveTime(new Date());
                callbackDataMapper.insert(callbackData);
                
                logger.error("批次处理失败 - originalTaskId={}, batchNumber={}", originalTaskId, batchNumber);
                evictTaskCaches(userId, originalTaskId);
                return;
            }

            if (batchNumber != null && batchNumber == 1) {
                List<CallbackData> tempCallbacks = callbackDataMapper.selectList(
                        new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<CallbackData>()
                                .eq("task_id", originalTaskId)
                );
                
                if (!tempCallbacks.isEmpty()) {
                    StringBuilder mergedData = new StringBuilder(data);
                    
                    for (CallbackData tempCallback : tempCallbacks) {
                        try {
                            String tempData = tempCallback.getData();
                            if (tempData != null) {
                                String decodedTempData = new String(Base64.getDecoder().decode(tempData), StandardCharsets.UTF_8);
                                mergedData.append("\n").append(decodedTempData);
                            }
                        } catch (Exception e) {
                            logger.error("解码临时回调数据失败: {}", e.getMessage());
                        }
                    }
                    
                    CallbackData callbackData = new CallbackData();
                    callbackData.setId(ShortIdUtil.generateShortId());
                    callbackData.setTaskId(originalTaskId);
                    callbackData.setOriginalTaskId(originalTaskId);
                    callbackData.setUserId(userId);
                    callbackData.setData(Base64.getEncoder().encodeToString(mergedData.toString().getBytes(StandardCharsets.UTF_8)));
                    callbackData.setReceiveTime(new Date());
                    callbackDataMapper.insert(callbackData);
                    
                    for (CallbackData tempCallback : tempCallbacks) {
                        callbackDataMapper.deleteById(tempCallback.getId());
                    }
                    
                    logger.info("首批回调数据已插入并合并临时数据 - originalTaskId={}", originalTaskId);
                } else {
                    CallbackData callbackData = new CallbackData();
                    callbackData.setId(ShortIdUtil.generateShortId());
                    callbackData.setTaskId(originalTaskId);
                    callbackData.setOriginalTaskId(originalTaskId);
                    callbackData.setUserId(userId);
                    callbackData.setData(Base64.getEncoder().encodeToString(data.getBytes(StandardCharsets.UTF_8)));
                    callbackData.setReceiveTime(new Date());
                    callbackDataMapper.insert(callbackData);
                    logger.info("首批回调数据已插入 - originalTaskId={}", originalTaskId);
                }
            } else if (batchNumber != null && batchNumber > 1) {
                int retryCount = 0;
                int maxRetries = 3;
                CallbackData firstCallback = null;
                
                while (firstCallback == null && retryCount < maxRetries) {
                    firstCallback = callbackDataMapper.selectOne(
                            new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<CallbackData>()
                                    .eq("task_id", originalTaskId)
                                    .orderByAsc("receive_time")
                                    .last("LIMIT 1")
                    );
                    
                    if (firstCallback == null) {
                        retryCount++;
                        logger.warn("未找到首批回调数据，等待重试 - originalTaskId={}, 重试次数={}", originalTaskId, retryCount);
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                }
                
                if (firstCallback != null) {
                    String existingData = firstCallback.getData();
                    String decodedExistingData = new String(Base64.getDecoder().decode(existingData), StandardCharsets.UTF_8);
                    String newDecodedData = decodedExistingData + "\n" + data;
                    String newEncodedData = Base64.getEncoder().encodeToString(newDecodedData.getBytes(StandardCharsets.UTF_8));
                    
                    firstCallback.setData(newEncodedData);
                    firstCallback.setReceiveTime(new Date());
                    callbackDataMapper.updateById(firstCallback);
                    logger.info("后续批次回调数据已追加 - originalTaskId={}, batchNumber={}", originalTaskId, batchNumber);
                } else {
                    CallbackData callbackData = new CallbackData();
                    callbackData.setId(ShortIdUtil.generateShortId());
                    callbackData.setTaskId(originalTaskId);
                    callbackData.setOriginalTaskId(originalTaskId);
                    callbackData.setUserId(userId);
                    String batchMarkedData = "[Batch " + batchNumber + "]\n" + data;
                    callbackData.setData(Base64.getEncoder().encodeToString(batchMarkedData.getBytes(StandardCharsets.UTF_8)));
                    callbackData.setReceiveTime(new Date());
                    callbackDataMapper.insert(callbackData);
                    logger.warn("多次重试后仍未找到首批回调数据，创建临时回调数据 - originalTaskId={}, batchNumber={}", originalTaskId, batchNumber);
                }
            } else {
                CallbackData callbackData = new CallbackData();
                callbackData.setId(ShortIdUtil.generateShortId());
                callbackData.setTaskId(originalTaskId);
                callbackData.setOriginalTaskId(originalTaskId);
                callbackData.setUserId(userId);
                callbackData.setData(Base64.getEncoder().encodeToString(data.getBytes(StandardCharsets.UTF_8)));
                callbackData.setReceiveTime(new Date());
                callbackDataMapper.insert(callbackData);
                logger.info("非批次任务回调数据已插入 - taskId={}", taskId);
            }

            Task currentTask = taskMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Task>()
                            .eq("task_id", originalTaskId)
            );
            
            if (currentTask != null) {
                int totalBatches = currentTask.getTotalBatches();
                
                Task updateTask = new Task();
                updateTask.setUpdateTime(new Date());
                
                if (batchNumber != null) {
                    int progress = (int) Math.round((double) batchNumber / totalBatches * 100);
                    updateTask.setProgress(progress);
                    
                    if (batchNumber >= totalBatches) {
                        updateTask.setStatus("COMPLETED");
                        logger.info("最后批次回调完成 - originalTaskId={}, batchNumber={}, totalBatches={}", originalTaskId, batchNumber, totalBatches);
                    } else {
                        updateTask.setStatus("PROCESSING");
                        logger.info("批次回调进行中 - originalTaskId={}, batchNumber={}/{}", originalTaskId, batchNumber, totalBatches);
                    }
                } else {
                    updateTask.setStatus("COMPLETED");
                    updateTask.setProgress(100);
                    logger.info("非批次任务回调完成 - originalTaskId={}", originalTaskId);
                }
                
                taskMapper.update(updateTask, new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Task>().eq("task_id", originalTaskId));
            }

            idempotencyService.markCallbackProcessed(taskId);
            
            logger.info("回调处理完成 - taskId={}, originalTaskId={}", taskId, originalTaskId);
            evictTaskCaches(userId, originalTaskId);

        } catch (Exception e) {
            logger.error("处理回调数据时发生异常: {}", e.getMessage(), e);
        }
    }

    public List<CallbackData> getTaskResults(String taskId) {
        List<CallbackData> results = callbackDataMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<CallbackData>()
                        .eq("task_id", taskId)
                        .orderByDesc("receive_time")
        );
        return decodeCallbackDataResults(results);
    }
    
    @Cacheable(cacheNames = "taskResultsByUserAndTask", key = "#userId + ':' + #taskId")
    public List<CallbackData> getTaskResultsByTaskIdAndUserId(String taskId, String userId) {
        Task task = taskMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Task>()
                        .eq("task_id", taskId)
                        .eq("user_id", userId)
        );
        
        if (task == null) {
            return new ArrayList<>();
        }
        
        List<CallbackData> results = callbackDataMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<CallbackData>()
                        .eq("task_id", taskId)
                        .orderByDesc("receive_time")
        );
        return decodeCallbackDataResults(results);
    }

    public List<CallbackData> getTaskResultsByOriginalTaskId(String originalTaskId) {
        List<CallbackData> results = callbackDataMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<CallbackData>()
                        .eq("original_task_id", originalTaskId)
                        .orderByDesc("receive_time")
        );
        return decodeCallbackDataResults(results);
    }
    
    @Cacheable(cacheNames = "originalTaskResultsByUserAndOriginal", key = "#userId + ':' + #originalTaskId")
    public List<CallbackData> getTaskResultsByOriginalTaskIdAndUserId(String originalTaskId, String userId) {
        Task originalTask = taskMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Task>()
                        .eq("original_task_id", originalTaskId)
                        .eq("user_id", userId)
        );
        
        if (originalTask == null) {
            return new ArrayList<>();
        }
        
        List<CallbackData> results = callbackDataMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<CallbackData>()
                        .eq("original_task_id", originalTaskId)
                        .orderByDesc("receive_time")
        );
        return decodeCallbackDataResults(results);
    }
    
    private List<CallbackData> decodeCallbackDataResults(List<CallbackData> results) {
        for (CallbackData result : results) {
            try {
                String encodedData = result.getData();
                if (encodedData != null) {
                    String decodedData = new String(Base64.getDecoder().decode(encodedData), StandardCharsets.UTF_8);
                    result.setData(decodedData);
                }
            } catch (Exception e) {
                logger.error("解码回调数据失败: {}", e.getMessage());
            }
        }
        return results;
    }

    @CacheEvict(cacheNames = "taskResultsByUserAndTask", key = "#userId + ':' + #taskId", condition = "#userId != null")
    public void deleteByTaskId(String taskId, String userId) {
        callbackDataMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<CallbackData>().eq("task_id", taskId));
        evictTaskCaches(userId, taskId);
    }

    private void evictTaskCaches(String userId, String taskId) {
        if (cacheManager == null || userId == null || taskId == null) {
            return;
        }
        Cache tasksByUser = cacheManager.getCache("tasksByUser");
        if (tasksByUser != null) {
            tasksByUser.evict(userId);
        }
        Cache taskResults = cacheManager.getCache("taskResultsByUserAndTask");
        if (taskResults != null) {
            taskResults.evict(userId + ":" + taskId);
        }
        Cache originalResults = cacheManager.getCache("originalTaskResultsByUserAndOriginal");
        if (originalResults != null) {
            originalResults.evict(userId + ":" + taskId);
        }
    }
}
