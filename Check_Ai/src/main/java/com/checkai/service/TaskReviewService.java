package com.checkai.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.checkai.dto.TaskReviewRequest;
import com.checkai.dto.TaskReviewVO;
import com.checkai.entity.Task;
import com.checkai.entity.TaskReview;
import com.checkai.mapper.TaskReviewMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class TaskReviewService {
    private static final Set<String> REVIEW_STATUSES = Set.of(
            "UNREVIEWED", "REVIEWING", "CONFIRMED", "REJECTED", "CLOSED"
    );
    private static final Set<String> RISK_LEVELS = Set.of(
            "LOW", "MEDIUM", "HIGH", "CRITICAL"
    );

    private final TaskReviewMapper taskReviewMapper;
    private final TaskService taskService;

    public TaskReviewService(TaskReviewMapper taskReviewMapper, TaskService taskService) {
        this.taskReviewMapper = taskReviewMapper;
        this.taskService = taskService;
    }

    public TaskReview getReviewByTaskIdAndUserId(String taskId, String userId) {
        return taskReviewMapper.selectOne(
                new QueryWrapper<TaskReview>()
                        .eq("task_id", taskId)
                        .eq("user_id", userId)
                        .last("limit 1")
        );
    }

    public TaskReview saveOrUpdateReview(String taskId, String userId, TaskReviewRequest request) {
        Task task = taskService.getTaskByTaskIdAndUserId(taskId, userId);
        if (task == null) {
            throw new IllegalArgumentException("任务不存在或无权访问");
        }

        TaskReview taskReview = getReviewByTaskIdAndUserId(taskId, userId);
        Date now = new Date();
        if (taskReview == null) {
            taskReview = new TaskReview();
            taskReview.setTaskId(taskId);
            taskReview.setUserId(userId);
            taskReview.setCreateTime(now);
            taskReview.setReviewStatus("UNREVIEWED");
        }

        String reviewStatus = normalizeReviewStatus(request.getReviewStatus());
        String riskLevel = normalizeRiskLevel(request.getRiskLevel());

        taskReview.setReviewStatus(reviewStatus != null ? reviewStatus : "UNREVIEWED");
        taskReview.setRiskLevel(riskLevel);
        taskReview.setTags(cleanText(request.getTags()));
        taskReview.setRemark(cleanText(request.getRemark()));
        taskReview.setReviewResult(cleanText(request.getReviewResult()));
        taskReview.setReviewer(userId);
        taskReview.setReviewTime(now);
        taskReview.setUpdateTime(now);

        if (taskReview.getId() == null) {
            taskReviewMapper.insert(taskReview);
        } else {
            taskReviewMapper.updateById(taskReview);
        }
        return taskReview;
    }

    public List<TaskReviewVO> listReviewWorkbench(String userId, String reviewStatus) {
        String normalizedStatus = normalizeReviewStatus(reviewStatus);

        List<Task> tasks = taskService.getTasksByUserId(userId);
        List<TaskReview> reviews = taskReviewMapper.selectList(
                new QueryWrapper<TaskReview>()
                        .eq("user_id", userId)
        );

        Map<String, TaskReview> reviewMap = new HashMap<>();
        for (TaskReview review : reviews) {
            reviewMap.put(review.getTaskId(), review);
        }

        List<TaskReviewVO> result = new ArrayList<>();
        for (Task task : tasks) {
            TaskReview review = reviewMap.get(task.getTaskId());
            String currentReviewStatus = review != null ? review.getReviewStatus() : "UNREVIEWED";

            if (normalizedStatus != null && !normalizedStatus.equals(currentReviewStatus)) {
                continue;
            }

            TaskReviewVO item = new TaskReviewVO();
            item.setTaskId(task.getTaskId());
            item.setTaskStatus(task.getStatus());
            item.setTaskProgress(task.getProgress());
            item.setTaskCreateTime(task.getCreateTime());
            item.setTaskUpdateTime(task.getUpdateTime());
            item.setReviewStatus(currentReviewStatus);

            if (review != null) {
                item.setRiskLevel(review.getRiskLevel());
                item.setTags(review.getTags());
                item.setRemark(review.getRemark());
                item.setReviewResult(review.getReviewResult());
                item.setReviewer(review.getReviewer());
                item.setReviewTime(review.getReviewTime());
                item.setReviewUpdateTime(review.getUpdateTime());
            }
            result.add(item);
        }
        return result;
    }

    private String normalizeReviewStatus(String reviewStatus) {
        if (reviewStatus == null || reviewStatus.isBlank()) {
            return null;
        }
        String normalized = reviewStatus.trim().toUpperCase();
        return REVIEW_STATUSES.contains(normalized) ? normalized : null;
    }

    private String normalizeRiskLevel(String riskLevel) {
        if (riskLevel == null || riskLevel.isBlank()) {
            return null;
        }
        String normalized = riskLevel.trim().toUpperCase();
        return RISK_LEVELS.contains(normalized) ? normalized : null;
    }

    private String cleanText(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.trim();
        return cleaned.isEmpty() ? null : cleaned;
    }
}
