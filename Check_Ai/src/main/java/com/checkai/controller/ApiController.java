package com.checkai.controller;

import com.checkai.dto.ExcelData;
import com.checkai.dto.LocalDataRequest;
import com.checkai.entity.CallbackData;
import com.checkai.entity.LogisticsOrder;
import com.checkai.entity.Task;
import com.checkai.service.CallbackService;
import com.checkai.service.ExcelService;
import com.checkai.service.LoginsticsService;
import com.checkai.service.TaskService;
import com.checkai.service.WorkflowService;
import com.checkai.util.CurrentUserHolder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api")
@Tag(name = "API 控制器", description = "系统核心 API 接口")
public class ApiController {
    private static final Set<String> TASK_STATUS_FILTERS = Set.of(
            "PENDING", "SENT", "PROCESSING", "COMPLETED", "FAILED", "CANCELLED"
    );

    @Autowired
    private ExcelService excelService;

    @Autowired
    private WorkflowService workflowService;

    @Autowired
    private CallbackService callbackService;

    @Autowired
    private TaskService taskService;

    @Autowired
    private LoginsticsService loginsticsService;

    @PostMapping("/upload-excel")
    @Operation(summary = "上传 Excel 文件", description = "解析 Excel 文件并创建异步检测任务")
    public ResponseEntity<Map<String, Object>> uploadExcel(@RequestParam("file") MultipartFile file) {
        Map<String, Object> result = new HashMap<>();

        try {
            if (file.isEmpty()) {
                result.put("error", "文件不能为空");
                return ResponseEntity.badRequest().body(result);
            }

            ExcelData excelData = excelService.parseExcel(file.getInputStream());
            String userId = getCurrentUserIdOrDefault();
            String taskId = workflowService.createTaskAndSendToQueue(excelData, userId);

            result.put("success", true);
            result.put("taskId", taskId);
            result.put("message", "文件上传成功，任务已提交处理");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
            return ResponseEntity.status(500).body(result);
        }
    }

    @PostMapping("/callback")
    @Operation(summary = "接收回调结果", description = "根据 taskId 保存外部处理结果")
    public ResponseEntity<Map<String, Object>> callback(
            @RequestBody String data,
            @RequestParam String taskId) {
        Map<String, Object> result = new HashMap<>();

        try {
            if (taskId == null || data == null || data.isEmpty()) {
                result.put("status", "error");
                result.put("message", "缺少必要参数：taskId 或 data");
                return ResponseEntity.badRequest().body(result);
            }

            callbackService.processCallback(taskId, data);

            result.put("status", "success");
            result.put("message", "回调数据处理成功");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", "处理回调数据失败: " + e.getMessage());
            return ResponseEntity.status(500).body(result);
        }
    }

    @GetMapping("/callback")
    @Operation(summary = "接收回调结果（GET）", description = "兼容 GET 方式提交回调数据")
    public ResponseEntity<Map<String, Object>> callbackGet(@RequestParam String taskId, @RequestParam String data) {
        Map<String, Object> result = new HashMap<>();

        try {
            if (taskId == null || data == null) {
                result.put("status", "error");
                result.put("message", "缺少必要参数");
                return ResponseEntity.badRequest().body(result);
            }

            callbackService.processCallback(taskId, data);

            result.put("status", "success");
            result.put("message", "回调数据处理成功");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", "处理回调数据失败: " + e.getMessage());
            return ResponseEntity.status(500).body(result);
        }
    }

    @GetMapping("/test")
    @Operation(summary = "接口测试", description = "检查 API 服务是否正常运行")
    public ResponseEntity<Map<String, Object>> test() {
        Map<String, Object> result = new HashMap<>();
        result.put("service", "AI Check System API");
        result.put("status", "运行正常");
        result.put("version", "1.0.0");
        return ResponseEntity.ok(result);
    }

    @GetMapping("/tasks")
    @Operation(summary = "获取任务列表", description = "获取当前用户的任务列表，可按状态过滤")
    public ResponseEntity<Map<String, Object>> getTasks(@RequestParam(required = false) String status) {
        Map<String, Object> result = new HashMap<>();

        try {
            String userId = CurrentUserHolder.getUserId();
            if (userId == null) {
                result.put("success", false);
                result.put("error", "用户未登录");
                return ResponseEntity.status(401).body(result);
            }

            List<Task> tasks;
            String normalizedStatus = normalizeTaskStatus(status);
            if (normalizedStatus == null) {
                tasks = taskService.getTasksByUserId(userId);
            } else {
                tasks = taskService.getTasksByUserIdAndStatus(userId, normalizedStatus);
            }

            result.put("success", true);
            result.put("tasks", tasks);
            if (normalizedStatus != null) {
                result.put("filterStatus", normalizedStatus);
            }
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", "获取任务列表失败: " + e.getMessage());
            return ResponseEntity.status(500).body(result);
        }
    }

    @GetMapping("/task/{taskId}/results")
    @Operation(summary = "获取任务结果", description = "根据任务 ID 获取当前用户的处理结果")
    public ResponseEntity<Map<String, Object>> getTaskResults(@PathVariable String taskId) {
        Map<String, Object> result = new HashMap<>();

        try {
            String userId = CurrentUserHolder.getUserId();
            if (userId == null) {
                result.put("success", false);
                result.put("error", "用户未登录");
                return ResponseEntity.status(401).body(result);
            }

            List<CallbackData> results = callbackService.getTaskResultsByTaskIdAndUserId(taskId, userId);

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
    @Operation(summary = "获取原始任务结果", description = "根据原始任务 ID 获取当前用户的所有批次结果")
    public ResponseEntity<Map<String, Object>> getOriginalTaskResults(@PathVariable String originalTaskId) {
        Map<String, Object> result = new HashMap<>();

        try {
            String userId = CurrentUserHolder.getUserId();
            if (userId == null) {
                result.put("success", false);
                result.put("error", "用户未登录");
                return ResponseEntity.status(401).body(result);
            }

            List<CallbackData> results = callbackService.getTaskResultsByOriginalTaskIdAndUserId(originalTaskId, userId);

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
    @Operation(summary = "上传 Excel 到 LangChain", description = "上传 Excel 文件并调用 LangChain Agent 处理")
    public ResponseEntity<Map<String, Object>> uploadExcelLangChain(@RequestParam("file") MultipartFile file) {
        Map<String, Object> result = new HashMap<>();

        try {
            if (file.isEmpty()) {
                result.put("success", false);
                result.put("error", "文件不能为空");
                return ResponseEntity.badRequest().body(result);
            }

            String userId = getCurrentUserIdOrDefault();
            Map<String, Object> processResult = workflowService.processExcelWithLangChain(file, userId);

            result.put("success", true);
            result.put("taskId", processResult.get("taskId"));
            result.put("message", "文件上传成功，LangChain 任务已提交处理");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
            return ResponseEntity.status(500).body(result);
        }
    }

    @DeleteMapping("/task/{taskId}")
    @Operation(summary = "删除任务", description = "根据任务 ID 删除当前用户的任务和结果")
    public ResponseEntity<Map<String, Object>> deleteTask(@PathVariable String taskId) {
        Map<String, Object> result = new HashMap<>();

        try {
            String userId = CurrentUserHolder.getUserId();
            if (userId == null) {
                result.put("success", false);
                result.put("error", "用户未登录");
                return ResponseEntity.status(401).body(result);
            }

            callbackService.deleteByTaskId(taskId, userId);
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

    @PostMapping("/task/{taskId}/cancel")
    @Operation(summary = "取消任务", description = "取消 PENDING、SENT 或 PROCESSING 状态的任务")
    public ResponseEntity<Map<String, Object>> cancelTask(@PathVariable String taskId) {
        Map<String, Object> result = new HashMap<>();
        try {
            String userId = CurrentUserHolder.getUserId();
            if (userId == null) {
                result.put("success", false);
                result.put("error", "用户未登录");
                return ResponseEntity.status(401).body(result);
            }

            boolean cancelled = taskService.cancelTask(taskId, userId);
            if (!cancelled) {
                result.put("success", false);
                result.put("error", "任务不存在、无权限或当前状态不可取消");
                return ResponseEntity.badRequest().body(result);
            }

            result.put("success", true);
            result.put("message", "任务取消成功");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
            return ResponseEntity.status(500).body(result);
        }
    }

    @PostMapping("/task/{taskId}/retry")
    @Operation(summary = "重试任务", description = "重试 FAILED 或 CANCELLED 状态的 Excel 任务")
    public ResponseEntity<Map<String, Object>> retryTask(@PathVariable String taskId) {
        Map<String, Object> result = new HashMap<>();
        try {
            String userId = CurrentUserHolder.getUserId();
            if (userId == null) {
                result.put("success", false);
                result.put("error", "用户未登录");
                return ResponseEntity.status(401).body(result);
            }

            String newTaskId = workflowService.retryTask(taskId, userId);
            result.put("success", true);
            result.put("newTaskId", newTaskId);
            result.put("message", "任务重试已提交");
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException | IllegalStateException e) {
            result.put("success", false);
            result.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(result);
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
            return ResponseEntity.status(500).body(result);
        }
    }

    @PostMapping("/submit-local-data")
    @Operation(summary = "提交本地数据", description = "从本地物流订单中选择数据并提交处理")
    public ResponseEntity<Map<String, Object>> submitLocalData(@RequestBody LocalDataRequest request) {
        Map<String, Object> result = new HashMap<>();

        try {
            String userId = getCurrentUserIdOrDefault();

            boolean hasOrderIds = request.getOrderIds() != null && !request.getOrderIds().isEmpty();
            boolean hasWaybillNos = request.getWaybillNos() != null && !request.getWaybillNos().isEmpty();
            if (!hasOrderIds && !hasWaybillNos) {
                result.put("success", false);
                result.put("error", "请选择要提交的数据");
                return ResponseEntity.badRequest().body(result);
            }

            List<LogisticsOrder> orders = new ArrayList<>();
            Set<Long> addedOrderIds = new HashSet<>();

            if (hasOrderIds) {
                for (Long orderId : request.getOrderIds()) {
                    if (orderId == null) {
                        continue;
                    }
                    LogisticsOrder order = loginsticsService.logisticsSelectById(orderId, userId);
                    if (order != null && addedOrderIds.add(order.getId())) {
                        orders.add(order);
                    }
                }
            }

            if (hasWaybillNos) {
                for (String waybillNo : request.getWaybillNos()) {
                    LogisticsOrder order = loginsticsService.logisticsSelectByWaybillNo(waybillNo, userId);
                    if (order != null && addedOrderIds.add(order.getId())) {
                        orders.add(order);
                    }
                }
            }

            if (orders.isEmpty()) {
                result.put("success", false);
                result.put("error", "未找到有效的订单数据，请刷新本地数据后重试");
                return ResponseEntity.badRequest().body(result);
            }

            String mode = request.getMode();
            String taskId;

            if ("langchain".equals(mode)) {
                Map<String, Object> langchainResult = workflowService.processLocalDataWithLangChain(orders, userId);
                taskId = (String) langchainResult.get("taskId");
                result.put("response", langchainResult.get("response"));
            } else {
                taskId = workflowService.createTaskFromLocalData(orders, userId);
            }

            result.put("success", true);
            result.put("taskId", taskId);
            result.put("message", "已选择 " + orders.size() + " 条数据并提交处理");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
            return ResponseEntity.status(500).body(result);
        }
    }

    private String getCurrentUserIdOrDefault() {
        String userId = CurrentUserHolder.getUserId();
        return userId == null ? "test-user" : userId;
    }

    private String normalizeTaskStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        String normalized = status.trim().toUpperCase();
        return TASK_STATUS_FILTERS.contains(normalized) ? normalized : null;
    }
}
