package com.checkai.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.checkai.entity.Task;
import com.checkai.mapper.TaskMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

/**
 * 任务服务类，处理任务相关的业务逻辑
 */
@Service
public class TaskService {
    private static final Logger logger = LoggerFactory.getLogger(TaskService.class);

    @Autowired
    private TaskMapper taskMapper;

    /**
     * 获取所有任务列表
     * @return 任务列表
     */
    public List<Task> getAllTasks() {
        logger.info("开始获取所有任务列表");
        // 查询所有任务，按创建时间倒序排列
        QueryWrapper<Task> queryWrapper = new QueryWrapper<>();
        queryWrapper.orderByDesc("create_time");
        List<Task> tasks = taskMapper.selectList(queryWrapper);
        logger.info("获取所有任务列表成功，共 {} 个任务", tasks.size());
        return tasks;
    }

    /**
     * 根据用户ID获取任务列表
     * @param userId 用户ID
     * @return 任务列表
     */
    @Cacheable(cacheNames = "tasksByUser", key = "#userId")
    public List<Task> getTasksByUserId(String userId) {
        QueryWrapper<Task> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", userId)
                .orderByDesc("create_time");
        return taskMapper.selectList(queryWrapper);
    }

    public List<Task> getTasksByUserIdAndStatus(String userId, String status) {
        QueryWrapper<Task> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", userId)
                .eq("status", status)
                .orderByDesc("create_time");
        return taskMapper.selectList(queryWrapper);
    }

    /**
     * 根据原始任务ID获取任务列表（包括所有批次）
     * @param originalTaskId 原始任务ID
     * @return 任务列表
     */
    public List<Task> getTasksByOriginalTaskId(String originalTaskId) {
        QueryWrapper<Task> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("original_task_id", originalTaskId)
                .orderByAsc("batch_number");
        return taskMapper.selectList(queryWrapper);
    }

    /**
     * 根据任务ID获取任务详情
     * @param taskId 任务ID
     * @return 任务详情
     */
    public Task getTaskById(String taskId) {
        return taskMapper.selectById(taskId);
    }

    public Task getTaskByTaskIdAndUserId(String taskId, String userId) {
        return taskMapper.selectOne(
                new QueryWrapper<Task>()
                        .eq("task_id", taskId)
                        .eq("user_id", userId)
        );
    }

    @CacheEvict(cacheNames = "tasksByUser", key = "#userId")
    public boolean cancelTask(String taskId, String userId) {
        Task task = getTaskByTaskIdAndUserId(taskId, userId);
        if (task == null) {
            return false;
        }
        String status = task.getStatus();
        if (!"PENDING".equals(status) && !"SENT".equals(status) && !"PROCESSING".equals(status)) {
            return false;
        }
        Task updateTask = new Task();
        updateTask.setStatus("CANCELLED");
        updateTask.setUpdateTime(new Date());
        return taskMapper.update(updateTask,
                new QueryWrapper<Task>()
                        .eq("task_id", taskId)
                        .eq("user_id", userId)
        ) > 0;
    }

    /**
     * 根据任务ID和用户ID删除任务
     * @param taskId 任务ID
     * @param userId 用户ID
     */
    @CacheEvict(cacheNames = "tasksByUser", key = "#userId")
    public void deleteTask(String taskId, String userId) {
        QueryWrapper<Task> queryWrapper = new QueryWrapper<>();
        // 这里的taskId是业务task_id（API路径也是 /task/{taskId}），不是主键id
        queryWrapper.eq("task_id", taskId)
                .eq("user_id", userId);
        taskMapper.delete(queryWrapper);
    }
}
