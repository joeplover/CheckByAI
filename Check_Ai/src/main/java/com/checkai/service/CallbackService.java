package com.checkai.service;

import com.checkai.entity.CallbackData;
import com.checkai.entity.Task;
import com.checkai.mapper.CallbackDataMapper;
import com.checkai.mapper.TaskMapper;
import com.checkai.util.ShortIdUtil;
import org.springframework.beans.factory.annotation.Autowired;
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

            // 获取userId
            String userId = task.getUserId();

            // 检查是否是首批回调（批次1）
            if (batchNumber != null && batchNumber == 1) {
                // 首批回调，直接插入数据库
                CallbackData callbackData = new CallbackData();
                callbackData.setId(ShortIdUtil.generateShortId()); // 生成8位短ID
                callbackData.setTaskId(originalTaskId); // 使用原始taskId作为callbackData的taskId
                callbackData.setOriginalTaskId(originalTaskId);
                callbackData.setUserId(userId);
                callbackData.setData(Base64.getEncoder().encodeToString(data.getBytes(StandardCharsets.UTF_8)));
                callbackData.setReceiveTime(new Date());
                callbackDataMapper.insert(callbackData);
                System.out.println("首批回调数据已插入 - originalTaskId: " + originalTaskId);
            } else if (batchNumber != null && batchNumber > 1) {
                // 后续批次回调，追加到首批数据后
                // 查询首批回调数据
                CallbackData firstCallback = callbackDataMapper.selectOne(
                        new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<CallbackData>()
                                .eq("task_id", originalTaskId)
                                .orderByAsc("receive_time")
                                .last("LIMIT 1")
                );
                
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
                    // 如果没有找到首批回调数据，直接插入
                    CallbackData callbackData = new CallbackData();
                    callbackData.setId(ShortIdUtil.generateShortId()); // 生成8位短ID
                    callbackData.setTaskId(originalTaskId);
                    callbackData.setOriginalTaskId(originalTaskId);
                    callbackData.setUserId(userId);
                    callbackData.setData(Base64.getEncoder().encodeToString(data.getBytes(StandardCharsets.UTF_8)));
                    callbackData.setReceiveTime(new Date());
                    callbackDataMapper.insert(callbackData);
                    System.out.println("未找到首批回调数据，直接插入 - originalTaskId: " + originalTaskId);
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

            // 更新任务状态为已完成
            Task updateTask = new Task();
            updateTask.setStatus("COMPLETED");
            updateTask.setUpdateTime(new Date());
            updateTask.setProgress(task.getTotalProgress()); // 设置进度为100%
            taskMapper.update(updateTask, new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Task>().eq("task_id", originalTaskId));

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
    public void deleteByTaskId(String taskId) {
        callbackDataMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<CallbackData>().eq("task_id", taskId));
    }
}
