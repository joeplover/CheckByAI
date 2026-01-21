package com.checkai.service;

import com.checkai.entity.CallbackData;
import com.checkai.entity.Task;
import com.checkai.mapper.CallbackDataMapper;
import com.checkai.mapper.TaskMapper;
import com.checkai.util.ShortIdUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class CallbackService {

    @Autowired
    private CallbackDataMapper callbackDataMapper;

    @Autowired
    private TaskMapper taskMapper;

    private static final Pattern BATCH_TASK_ID_PATTERN = Pattern.compile("^(.*?)_batch_(\\d+)$");

    public void processCallback(String taskId, String data) {
        // 打印回调数据到控制台
        System.out.println("收到回调数据 - taskId: " + taskId + ", data: " + data);
        
        // 查询任务信息，检查taskId是否有效
        Task task = taskMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Task>().eq("task_id", taskId));
        
        // 如果taskId无效，只打印数据，不插入数据库
        if (task == null) {
            System.out.println("taskId无效，不插入数据库 - taskId: " + taskId);
            return;
        }
        
        // taskId有效，继续处理
        System.out.println("taskId有效，插入数据库 - taskId: " + taskId);
        
        // 解析taskId，获取原始taskId和批次信息
        String originalTaskId = taskId;
        Integer batchNumber = null;

        Matcher matcher = BATCH_TASK_ID_PATTERN.matcher(taskId);
        if (matcher.matches()) {
            originalTaskId = matcher.group(1);
            batchNumber = Integer.parseInt(matcher.group(2));
        }

        // 获取userId
        String userId = task.getUserId();

        // 保存回调数据到数据库
        CallbackData callbackData = new CallbackData();
        callbackData.setId(ShortIdUtil.generateShortId()); // 生成8位短ID
        callbackData.setTaskId(taskId);
        callbackData.setOriginalTaskId(originalTaskId);
        callbackData.setUserId(userId);
        callbackData.setData(data);
        callbackData.setReceiveTime(new Date());
        callbackDataMapper.insert(callbackData);

        // 更新任务状态
        Task updateTask = new Task();
        updateTask.setStatus("COMPLETED");
        updateTask.setUpdateTime(new Date());
        taskMapper.update(updateTask, new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Task>().eq("task_id", taskId));

        // 如果是批次任务，检查是否所有批次都已完成
        if (originalTaskId != null) {
            checkAllBatchesCompleted(originalTaskId);
        }
    }

    private void checkAllBatchesCompleted(String originalTaskId) {
        // 获取已完成的批次数量
        int completedCount = taskMapper.countCompletedBatches(originalTaskId);
        // 获取总批次数量
        Integer totalBatches = taskMapper.getTotalBatches(originalTaskId);

        if (totalBatches != null) {
            // 检查是否所有批次都已完成或超时失败
            long totalFailedCount = taskMapper.selectCount(
                    new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Task>()
                            .eq("original_task_id", originalTaskId)
                            .eq("status", "FAILED")
            );

            // 更新原始任务的进度
            updateTaskProgress(originalTaskId, completedCount, totalBatches);

            // 如果所有批次都已完成或失败
            if (completedCount + totalFailedCount >= totalBatches) {
                // 检查是否所有批次都成功完成
                boolean allSuccess = totalFailedCount == 0;
                
                // 更新所有批次的总进度
                List<Task> batchTasks = taskMapper.selectList(
                        new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Task>()
                                .eq("original_task_id", originalTaskId)
                );

                for (Task batchTask : batchTasks) {
                    batchTask.setProgress(totalBatches);
                    batchTask.setUpdateTime(new Date());
                    taskMapper.updateById(batchTask);
                }

                System.out.println("批次任务" + originalTaskId + "处理结束，结果: " + (allSuccess ? "成功" : "失败"));
            }
        }
    }

    /**
     * 更新任务进度
     */
    private void updateTaskProgress(String originalTaskId, int completedCount, int totalBatches) {
        // 查询所有批次任务
        List<Task> batchTasks = taskMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Task>()
                        .eq("original_task_id", originalTaskId)
        );

        // 更新每个批次任务的进度
        for (Task batchTask : batchTasks) {
            batchTask.setProgress(completedCount);
            batchTask.setUpdateTime(new Date());
            taskMapper.updateById(batchTask);
        }
    }

    /**
     * 根据任务ID获取任务结果
     * @param taskId 任务ID
     * @return 任务结果列表
     */
    public List<CallbackData> getTaskResults(String taskId) {
        return callbackDataMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<CallbackData>()
                        .eq("task_id", taskId)
                        .orderByDesc("receive_time")
        );
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
        
        return callbackDataMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<CallbackData>()
                        .eq("task_id", taskId)
                        .orderByDesc("receive_time")
        );
    }

    /**
     * 根据原始任务ID获取任务结果（包括所有批次）
     * @param originalTaskId 原始任务ID
     * @return 任务结果列表
     */
    public List<CallbackData> getTaskResultsByOriginalTaskId(String originalTaskId) {
        return callbackDataMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<CallbackData>()
                        .eq("original_task_id", originalTaskId)
                        .orderByDesc("receive_time")
        );
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
        
        return callbackDataMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<CallbackData>()
                        .eq("original_task_id", originalTaskId)
                        .orderByDesc("receive_time")
        );
    }
}
