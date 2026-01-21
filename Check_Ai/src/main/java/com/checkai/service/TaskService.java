package com.checkai.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.checkai.entity.Task;
import com.checkai.mapper.TaskMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

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
    public List<Task> getTasksByUserId(String userId) {
        QueryWrapper<Task> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", userId)
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
}