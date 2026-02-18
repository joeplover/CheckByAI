package com.checkai.service;

import com.checkai.entity.CallbackData;
import com.checkai.entity.Task;
import com.checkai.mapper.CallbackDataMapper;
import com.checkai.mapper.TaskMapper;
import com.checkai.util.ShortIdUtil;
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

    @Autowired
    private CallbackDataMapper callbackDataMapper;

    @Autowired
    private TaskMapper taskMapper;

    @Autowired(required = false)
    private CacheManager cacheManager;

    private static final Pattern BATCH_TASK_ID_PATTERN = Pattern.compile("^(.*?)_batch_(\\d+)$");

    public void processCallback(String taskId, String data) {
        try {
            // 打印回调数据到控制台
            System.out.println("收到回调数据 - taskId: " + taskId + ", data: " + data);
            
            // 解析taskId，获取原始taskId和批次信息
            String originalTaskId = taskId;
            Integer batchNumber = null;

            Matcher matcher = BATCH_TASK_ID_PATTERN.matcher(taskId);
            if (matcher.matches()) {
                originalTaskId = matcher.group(1);
                batchNumber = Integer.parseInt(matcher.group(2));
            }
            
            // 查询任务信息，检查originalTaskId是否有效
            Task task = taskMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Task>().eq("task_id", originalTaskId));
            
            // 如果taskId无效，只打印数据，不插入数据库
            if (task == null) {
                System.out.println("taskId无效，不插入数据库 - originalTaskId: " + originalTaskId);
                return;
            }
            
            // taskId有效，继续处理
            System.out.println("taskId有效，处理回调数据 - originalTaskId: " + originalTaskId);

            // 检查是否是失败回调
            boolean isFailure = false;
            try {
                // 尝试解析数据，检查是否包含失败信息
                if (data != null && (data.contains("error") || data.contains("failed") || data.contains("FAILURE"))) {
                    isFailure = true;
                    System.out.println("检测到失败回调 - originalTaskId: " + originalTaskId + ", batchNumber: " + batchNumber);
                }
            } catch (Exception e) {
                // 解析失败，继续处理
            }

            // 获取userId
            String userId = task.getUserId();

            // 如果是失败回调，直接更新任务状态为失败
            if (isFailure) {
                Task updateTask = new Task();
                updateTask.setStatus("FAILED");
                updateTask.setUpdateTime(new Date());
                updateTask.setProgress(task.getTotalProgress());
                taskMapper.update(updateTask, new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Task>().eq("task_id", originalTaskId));
                
                // 记录失败回调数据
                CallbackData callbackData = new CallbackData();
                callbackData.setId(ShortIdUtil.generateShortId());
                callbackData.setTaskId(originalTaskId);
                callbackData.setOriginalTaskId(originalTaskId);
                callbackData.setUserId(userId);
                String failureData = "[FAILURE]\n" + data;
                callbackData.setData(Base64.getEncoder().encodeToString(failureData.getBytes(StandardCharsets.UTF_8)));
                callbackData.setReceiveTime(new Date());
                callbackDataMapper.insert(callbackData);
                
                System.out.println("批次处理失败 - originalTaskId: " + originalTaskId + ", batchNumber: " + batchNumber);
                evictTaskCaches(userId, originalTaskId);
                return;
            }

            // 检查是否是首批回调（批次1）
        if (batchNumber != null && batchNumber == 1) {
            // 检查是否有临时回调数据（后续批次的回调数据）
            List<CallbackData> tempCallbacks = callbackDataMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<CallbackData>()
                            .eq("task_id", originalTaskId)
            );
            
            if (!tempCallbacks.isEmpty()) {
                // 有临时回调数据，需要合并
                StringBuilder mergedData = new StringBuilder(data);
                
                // 遍历临时回调数据，提取并合并数据
                for (CallbackData tempCallback : tempCallbacks) {
                    try {
                        String tempData = tempCallback.getData();
                        if (tempData != null) {
                            String decodedTempData = new String(Base64.getDecoder().decode(tempData), StandardCharsets.UTF_8);
                            mergedData.append("\n").append(decodedTempData);
                        }
                    } catch (Exception e) {
                        System.out.println("解码临时回调数据失败: " + e.getMessage());
                    }
                }
                
                // 创建合并后的回调数据
                CallbackData callbackData = new CallbackData();
                callbackData.setId(ShortIdUtil.generateShortId()); // 生成8位短ID
                callbackData.setTaskId(originalTaskId); // 使用原始taskId作为callbackData的taskId
                callbackData.setOriginalTaskId(originalTaskId);
                callbackData.setUserId(userId);
                callbackData.setData(Base64.getEncoder().encodeToString(mergedData.toString().getBytes(StandardCharsets.UTF_8)));
                callbackData.setReceiveTime(new Date());
                callbackDataMapper.insert(callbackData);
                
                // 删除临时回调数据
                for (CallbackData tempCallback : tempCallbacks) {
                    callbackDataMapper.deleteById(tempCallback.getId());
                }
                
                System.out.println("首批回调数据已插入并合并临时数据 - originalTaskId: " + originalTaskId);
            } else {
                // 没有临时回调数据，直接插入首批回调数据
                CallbackData callbackData = new CallbackData();
                callbackData.setId(ShortIdUtil.generateShortId()); // 生成8位短ID
                callbackData.setTaskId(originalTaskId); // 使用原始taskId作为callbackData的taskId
                callbackData.setOriginalTaskId(originalTaskId);
                callbackData.setUserId(userId);
                callbackData.setData(Base64.getEncoder().encodeToString(data.getBytes(StandardCharsets.UTF_8)));
                callbackData.setReceiveTime(new Date());
                callbackDataMapper.insert(callbackData);
                System.out.println("首批回调数据已插入 - originalTaskId: " + originalTaskId);
            }
        } else if (batchNumber != null && batchNumber > 1) {
            // 后续批次回调，追加到首批数据后
            // 尝试查找首批回调数据，最多重试3次
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
                    System.out.println("未找到首批回调数据，等待重试 - originalTaskId: " + originalTaskId + ", 重试次数: " + retryCount);
                    try {
                        Thread.sleep(1000); // 等待1秒后重试
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
            
            if (firstCallback != null) {
                // 追加数据到首批回调数据后
                String existingData = firstCallback.getData();
                // 解码现有数据，追加新数据后重新编码
                String decodedExistingData = new String(Base64.getDecoder().decode(existingData), StandardCharsets.UTF_8);
                String newDecodedData = decodedExistingData + "\n" + data;
                String newEncodedData = Base64.getEncoder().encodeToString(newDecodedData.getBytes(StandardCharsets.UTF_8));
                
                // 更新首批回调数据
                firstCallback.setData(newEncodedData);
                firstCallback.setReceiveTime(new Date());
                callbackDataMapper.updateById(firstCallback);
                System.out.println("后续批次回调数据已追加 - originalTaskId: " + originalTaskId + ", batchNumber: " + batchNumber);
            } else {
                // 如果多次重试后仍然没有找到首批回调数据，创建一个临时回调数据
                // 后续当首批回调到达时，再合并数据
                CallbackData callbackData = new CallbackData();
                callbackData.setId(ShortIdUtil.generateShortId()); // 生成8位短ID
                callbackData.setTaskId(originalTaskId);
                callbackData.setOriginalTaskId(originalTaskId);
                callbackData.setUserId(userId);
                // 在数据前添加批次标记，以便后续合并
                String batchMarkedData = "[Batch " + batchNumber + "]\n" + data;
                callbackData.setData(Base64.getEncoder().encodeToString(batchMarkedData.getBytes(StandardCharsets.UTF_8)));
                callbackData.setReceiveTime(new Date());
                callbackDataMapper.insert(callbackData);
                System.out.println("多次重试后仍未找到首批回调数据，创建临时回调数据 - originalTaskId: " + originalTaskId + ", batchNumber: " + batchNumber);
            }
        } else {
            // 非批次任务，直接插入
            CallbackData callbackData = new CallbackData();
            callbackData.setId(ShortIdUtil.generateShortId()); // 生成8位短ID
            callbackData.setTaskId(originalTaskId);
            callbackData.setOriginalTaskId(originalTaskId);
            callbackData.setUserId(userId);
            callbackData.setData(Base64.getEncoder().encodeToString(data.getBytes(StandardCharsets.UTF_8)));
            callbackData.setReceiveTime(new Date());
            callbackDataMapper.insert(callbackData);
            System.out.println("非批次任务回调数据已插入 - taskId: " + taskId);
        }

            // 查询当前任务状态
            Task currentTask = taskMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Task>()
                            .eq("task_id", originalTaskId)
            );
            
            if (currentTask != null) {
                // 计算已完成的批次数
                int totalBatches = currentTask.getTotalBatches();
                int completedBatches = 0;
                
                // 查询已完成的回调数据数量
                Long callbackCount = callbackDataMapper.selectCount(
                        new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<CallbackData>()
                                .eq("task_id", originalTaskId)
                );
                int completedCallbackCount = callbackCount != null ? callbackCount.intValue() : 0;
                
                completedBatches = completedCallbackCount;
                
                // 计算进度
                int progress = (int) Math.round((double) completedBatches / totalBatches * 100);
                
                // 更新任务状态和进度
                Task updateTask = new Task();
                updateTask.setUpdateTime(new Date());
                updateTask.setProgress(progress);
                
                // 只有当所有批次都完成时才更新为"COMPLETED"
                if (completedBatches >= totalBatches) {
                    updateTask.setStatus("COMPLETED");
                    System.out.println("所有批次回调完成 - originalTaskId: " + originalTaskId);
                } else {
                    updateTask.setStatus("PROCESSING");
                    System.out.println("批次回调进行中 - originalTaskId: " + originalTaskId + ", 已完成: " + completedBatches + "/" + totalBatches);
                }
                
                taskMapper.update(updateTask, new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Task>().eq("task_id", originalTaskId));
            }

            // 回调数据写入后，驱逐缓存（任务列表、任务结果）
            evictTaskCaches(userId, originalTaskId);

        } catch (Exception e) {
            // 处理异常
            System.out.println("处理回调数据时发生异常: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 根据任务ID获取任务结果
     * @param taskId 任务ID
     * @return 任务结果列表
     */
    public List<CallbackData> getTaskResults(String taskId) {
        List<CallbackData> results = callbackDataMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<CallbackData>()
                        .eq("task_id", taskId)
                        .orderByDesc("receive_time")
        );
        return decodeCallbackDataResults(results);
    }
    
    /**
     * 根据任务ID和用户ID获取任务结果
     * @param taskId 任务ID
     * @param userId 用户ID
     * @return 任务结果列表
     */
    @Cacheable(cacheNames = "taskResultsByUserAndTask", key = "#userId + ':' + #taskId")
    public List<CallbackData> getTaskResultsByTaskIdAndUserId(String taskId, String userId) {
        // 首先验证该任务是否属于当前用户
        Task task = taskMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Task>()
                        .eq("task_id", taskId)
                        .eq("user_id", userId)
        );
        
        if (task == null) {
            return new ArrayList<>(); // 返回空列表，表示该任务不属于当前用户
        }
        
        List<CallbackData> results = callbackDataMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<CallbackData>()
                        .eq("task_id", taskId)
                        .orderByDesc("receive_time")
        );
        return decodeCallbackDataResults(results);
    }

    /**
     * 根据原始任务ID获取任务结果（包括所有批次）
     * @param originalTaskId 原始任务ID
     * @return 任务结果列表
     */
    public List<CallbackData> getTaskResultsByOriginalTaskId(String originalTaskId) {
        List<CallbackData> results = callbackDataMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<CallbackData>()
                        .eq("original_task_id", originalTaskId)
                        .orderByDesc("receive_time")
        );
        return decodeCallbackDataResults(results);
    }
    
    /**
     * 根据原始任务ID和用户ID获取任务结果（包括所有批次）
     * @param originalTaskId 原始任务ID
     * @param userId 用户ID
     * @return 任务结果列表
     */
    @Cacheable(cacheNames = "originalTaskResultsByUserAndOriginal", key = "#userId + ':' + #originalTaskId")
    public List<CallbackData> getTaskResultsByOriginalTaskIdAndUserId(String originalTaskId, String userId) {
        // 首先验证该原始任务是否属于当前用户
        Task originalTask = taskMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Task>()
                        .eq("original_task_id", originalTaskId)
                        .eq("user_id", userId)
        );
        
        if (originalTask == null) {
            return new ArrayList<>(); // 返回空列表，表示该原始任务不属于当前用户
        }
        
        List<CallbackData> results = callbackDataMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<CallbackData>()
                        .eq("original_task_id", originalTaskId)
                        .orderByDesc("receive_time")
        );
        return decodeCallbackDataResults(results);
    }
    
    /**
     * 解码回调数据结果列表中的Base64编码数据
     * @param results 回调数据结果列表
     * @return 解码后的回调数据结果列表
     */
    private List<CallbackData> decodeCallbackDataResults(List<CallbackData> results) {
        for (CallbackData result : results) {
            try {
                String encodedData = result.getData();
                if (encodedData != null) {
                    String decodedData = new String(Base64.getDecoder().decode(encodedData), StandardCharsets.UTF_8);
                    result.setData(decodedData);
                }
            } catch (Exception e) {
                // 解码失败，保持原始数据
                System.out.println("解码回调数据失败: " + e.getMessage());
            }
        }
        return results;
    }

    /**
     * 根据任务ID删除回调数据
     * @param taskId 任务ID
     */
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
