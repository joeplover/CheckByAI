package com.checkai.controller;

import com.checkai.dto.ExcelData;
import com.checkai.entity.CallbackData;
import com.checkai.entity.Task;
import com.checkai.service.CallbackService;
import com.checkai.service.ExcelService;
import com.checkai.service.TaskService;
import com.checkai.service.WorkflowService;
import com.checkai.util.CurrentUserHolder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
@Tag(name = "API接口", description = "系统核心API接口")
public class ApiController {

    @Autowired
    private ExcelService excelService;

    @Autowired
    private WorkflowService workflowService;

    @Autowired
    private CallbackService callbackService;
    
    @Autowired
    private TaskService taskService;

    @PostMapping("/upload-excel")
    @Operation(summary = "上传Excel文件并处理", description = "上传Excel文件，解析数据并发送到工作流处理")
    public ResponseEntity<Map<String, Object>> uploadExcel(@RequestParam("file") MultipartFile file) {
        Map<String, Object> result = new HashMap<>();

        try {
            if (file.isEmpty()) {
                result.put("error", "空文件名");
                return ResponseEntity.badRequest().body(result);
            }

            // 解析Excel
            ExcelData excelData = excelService.parseExcel(file.getInputStream());

            // 从ThreadLocal中获取当前用户ID
            String userId = CurrentUserHolder.getUserId();
            // 如果获取不到，使用默认值
            if (userId == null) {
                userId = "test-user";
            }
            String taskId = workflowService.processExcelData(excelData, userId);

            result.put("success", true);
            result.put("taskId", taskId);
            result.put("message", "文件上传成功，任务已开始处理");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
            return ResponseEntity.status(500).body(result);
        }
    }

    @PostMapping("/callback")
    @Operation(summary = "工作流回调接口", description = "接收工作流处理结果的回调，支持通过请求体或请求参数传递数据")
    public ResponseEntity<Map<String, Object>> callback(
            @RequestBody(required = false) Map<String, Object> requestBody,
            @RequestParam(required = false) String taskId,
            @RequestParam(required = false) String data) {
        Map<String, Object> result = new HashMap<>();

        try {
            // 优先从请求体获取参数
            String finalTaskId = taskId;
            String finalData = data;
            
            // 如果请求体存在，优先使用请求体中的参数
            if (requestBody != null) {
                finalTaskId = (String) requestBody.getOrDefault("taskId", finalTaskId);
                finalData = (String) requestBody.getOrDefault("data", finalData);
            }

            // 检查参数是否存在
            if (finalTaskId == null || finalData == null) {
                result.put("status", "error");
                result.put("message", "缺少必要参数: taskId和data是必填项");
                return ResponseEntity.badRequest().body(result);
            }

            // 处理回调数据
            callbackService.processCallback(finalTaskId, finalData);

            result.put("status", "success");
            result.put("message", "回调数据已成功处理");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", "处理回调数据失败: " + e.getMessage());
            return ResponseEntity.status(500).body(result);
        }
    }

    @GetMapping("/callback")
    @Operation(summary = "工作流回调接口(GET方式)", description = "通过GET方式接收工作流处理结果的回调")
    public ResponseEntity<Map<String, Object>> callbackGet(@RequestParam String taskId, @RequestParam String data) {
        Map<String, Object> result = new HashMap<>();

        try {
            if (taskId == null || data == null) {
                result.put("status", "error");
                result.put("message", "缺少必要参数");
                return ResponseEntity.badRequest().body(result);
            }

            // 处理回调数据
            callbackService.processCallback(taskId, data);

            result.put("status", "success");
            result.put("message", "回调数据已成功处理");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", "处理回调数据失败: " + e.getMessage());
            return ResponseEntity.status(500).body(result);
        }
    }

    @GetMapping("/test")
    @Operation(summary = "测试接口", description = "测试服务是否正常运行")
    public ResponseEntity<Map<String, Object>> test() {
        Map<String, Object> result = new HashMap<>();
        result.put("service", "AI Check System API");
        result.put("status", "运行正常");
        result.put("version", "1.0.0");
        return ResponseEntity.ok(result);
    }
    
    @GetMapping("/tasks")
    @Operation(summary = "获取任务列表", description = "获取当前用户的任务列表")
    public ResponseEntity<Map<String, Object>> getTasks() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // 从ThreadLocal中获取当前用户ID
            String userId = CurrentUserHolder.getUserId();
            if (userId == null) {
                result.put("success", false);
                result.put("error", "未获取到用户信息");
                return ResponseEntity.status(401).body(result);
            }
            
            // 获取当前用户的任务列表
            java.util.List<Task> tasks = taskService.getTasksByUserId(userId);
            
            result.put("success", true);
            result.put("tasks", tasks);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", "获取任务列表失败: " + e.getMessage());
            return ResponseEntity.status(500).body(result);
        }
    }
    
    @GetMapping("/task/{taskId}/results")
    @Operation(summary = "获取任务结果", description = "根据任务ID获取当前用户的任务结果")
    public ResponseEntity<Map<String, Object>> getTaskResults(@PathVariable String taskId) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // 从ThreadLocal中获取当前用户ID
            String userId = CurrentUserHolder.getUserId();
            if (userId == null) {
                result.put("success", false);
                result.put("error", "未获取到用户信息");
                return ResponseEntity.status(401).body(result);
            }
            
            // 获取当前用户的任务结果
            java.util.List<CallbackData> results = callbackService.getTaskResultsByTaskIdAndUserId(taskId, userId);
            
            result.put("success", true);
            result.put("results", results);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", "获取任务结果失败: " + e.getMessage());
            return ResponseEntity.status(500).body(result);
        }
    }
    
    @GetMapping("/task/original/{originalTaskId}/results")
    @Operation(summary = "获取原始任务结果", description = "根据原始任务ID获取当前用户所有批次的任务结果")
    public ResponseEntity<Map<String, Object>> getOriginalTaskResults(@PathVariable String originalTaskId) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // 从ThreadLocal中获取当前用户ID
            String userId = CurrentUserHolder.getUserId();
            if (userId == null) {
                result.put("success", false);
                result.put("error", "未获取到用户信息");
                return ResponseEntity.status(401).body(result);
            }
            
            // 获取当前用户的原始任务结果
            java.util.List<CallbackData> results = callbackService.getTaskResultsByOriginalTaskIdAndUserId(originalTaskId, userId);
            
            result.put("success", true);
            result.put("results", results);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", "获取原始任务结果失败: " + e.getMessage());
            return ResponseEntity.status(500).body(result);
        }
    }
}
