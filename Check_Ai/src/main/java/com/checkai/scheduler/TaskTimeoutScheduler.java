package com.checkai.scheduler;

import com.checkai.entity.Task;
import com.checkai.mapper.TaskMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;

@Component
public class TaskTimeoutScheduler {

    @Autowired
    private TaskMapper taskMapper;

    /**
     * 每分钟检查一次任务超时
     */
    @Scheduled(cron = "0 * * * * ?")
    public void checkTaskTimeout() {
        // 查询所有未完成的任务
        List<Task> pendingTasks = taskMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Task>()
                        .in("status", List.of("PENDING", "SENT"))
        );

        Date now = new Date();
        for (Task task : pendingTasks) {
            // 检查是否超时
            if (task.getTimeoutTime() != null && now.after(task.getTimeoutTime())) {
                // 更新任务状态为失败
                task.setStatus("FAILED");
                task.setUpdateTime(now);
                taskMapper.updateById(task);
                
                System.out.println("任务超时失败: " + task.getTaskId());
                
                // 如果是批次任务，检查是否所有批次都已完成
                if (task.getOriginalTaskId() != null) {
                    checkAllBatchesCompleted(task.getOriginalTaskId());
                }
            }
        }
    }

    /**
     * 检查所有批次是否已完成
     */
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
}
