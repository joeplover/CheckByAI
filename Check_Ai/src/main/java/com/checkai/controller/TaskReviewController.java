package com.checkai.controller;

import com.checkai.dto.TaskReviewRequest;
import com.checkai.dto.TaskReviewVO;
import com.checkai.entity.TaskReview;
import com.checkai.service.TaskReviewService;
import com.checkai.util.CurrentUserHolder;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class TaskReviewController {
    private final TaskReviewService taskReviewService;

    public TaskReviewController(TaskReviewService taskReviewService) {
        this.taskReviewService = taskReviewService;
    }

    @GetMapping("/task/{taskId}/review")
    public ResponseEntity<Map<String, Object>> getTaskReview(@PathVariable String taskId) {
        Map<String, Object> result = new HashMap<>();
        String userId = CurrentUserHolder.getUserId();
        if (userId == null) {
            result.put("success", false);
            result.put("error", "未获取到用户信息");
            return ResponseEntity.status(401).body(result);
        }

        TaskReview review = taskReviewService.getReviewByTaskIdAndUserId(taskId, userId);
        result.put("success", true);
        result.put("data", review);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/task/{taskId}/review")
    public ResponseEntity<Map<String, Object>> saveTaskReview(
            @PathVariable String taskId,
            @RequestBody TaskReviewRequest request
    ) {
        Map<String, Object> result = new HashMap<>();
        String userId = CurrentUserHolder.getUserId();
        if (userId == null) {
            result.put("success", false);
            result.put("error", "未获取到用户信息");
            return ResponseEntity.status(401).body(result);
        }

        try {
            TaskReview review = taskReviewService.saveOrUpdateReview(taskId, userId, request);
            result.put("success", true);
            result.put("message", "复核已保存");
            result.put("data", review);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException exception) {
            result.put("success", false);
            result.put("error", exception.getMessage());
            return ResponseEntity.badRequest().body(result);
        } catch (Exception exception) {
            result.put("success", false);
            result.put("error", "保存复核失败: " + exception.getMessage());
            return ResponseEntity.status(500).body(result);
        }
    }

    @GetMapping("/task-reviews")
    public ResponseEntity<Map<String, Object>> listTaskReviews(
            @RequestParam(required = false) String reviewStatus
    ) {
        Map<String, Object> result = new HashMap<>();
        String userId = CurrentUserHolder.getUserId();
        if (userId == null) {
            result.put("success", false);
            result.put("error", "未获取到用户信息");
            return ResponseEntity.status(401).body(result);
        }

        try {
            List<TaskReviewVO> items = taskReviewService.listReviewWorkbench(userId, reviewStatus);
            result.put("success", true);
            result.put("items", items);
            return ResponseEntity.ok(result);
        } catch (Exception exception) {
            result.put("success", false);
            result.put("error", "获取复核列表失败: " + exception.getMessage());
            return ResponseEntity.status(500).body(result);
        }
    }
}
