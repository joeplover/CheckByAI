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
    @Operation(summary = "工作流回调接口", description = "接收工作流处理结果的回调，taskId从请求参数获取，data从请求体data获取")
    public ResponseEntity<Map<String, Object>> callback(
            @RequestBody String data,
            @RequestParam String taskId) {
        Map<String, Object> result = new HashMap<>();

        try {
            // 检查参数是否存在
            if (taskId == null || data == null || data.isEmpty()) {
                result.put("status", "error");
                result.put("message", "缺少必要参数: taskId是必填项，请求体data不能为空");
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

    @GetMapping("/callback")
    @Operation(summary = "工作流回调接口(GET方式)", description = "已废弃：通过GET方式接收工作流处理结果的回调，仅支持短文本数据")
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

    @PostMapping("/upload-excel-langchain")
    @Operation(summary = "上传Excel文件并调用LangChain Agent", description = "上传Excel文件，调用LangChain Agent API生成taskId，等待结果返回后插入数据库")
    public ResponseEntity<Map<String, Object>> uploadExcelLangChain(@RequestParam("file") MultipartFile file) {
        Map<String, Object> result = new HashMap<>();

        try {
            if (file.isEmpty()) {
                result.put("success", false);
                result.put("error", "空文件名");
                return ResponseEntity.badRequest().body(result);
            }

            // 从ThreadLocal中获取当前用户ID
            String userId = CurrentUserHolder.getUserId();
            // 如果获取不到，使用默认值
            if (userId == null) {
                userId = "test-user";
            }

            // 调用服务层方法处理文件上传和API调用
            Map<String, Object> processResult = workflowService.processExcelWithLangChain(file, userId);

            result.put("success", true);
            result.put("taskId", processResult.get("taskId"));
            result.put("message", "文件上传成功，任务已处理完成");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
            return ResponseEntity.status(500).body(result);
        }
    }

    @DeleteMapping("/task/{taskId}")
    @Operation(summary = "删除任务", description = "根据任务ID删除任务及其相关数据")
    public ResponseEntity<Map<String, Object>> deleteTask(@PathVariable String taskId) {
        Map<String, Object> result = new HashMap<>();

        try {
            // 从ThreadLocal中获取当前用户ID
            String userId = CurrentUserHolder.getUserId();
            if (userId == null) {
                result.put("success", false);
                result.put("error", "未获取到用户信息");
                return ResponseEntity.status(401).body(result);
            }

            // 删除任务及其相关数据
            // 1. 删除任务相关的回调数据
            callbackService.deleteByTaskId(taskId, userId);
            // 2. 删除任务
            taskService.deleteTask(taskId, userId);

            result.put("success", true);
            result.put("message", "任务删除成功");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
            return ResponseEntity.status(500).body(result);
        }
    }
}

