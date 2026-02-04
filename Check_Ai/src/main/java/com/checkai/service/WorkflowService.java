package com.checkai.service;

import com.checkai.dto.ExcelData;
import com.checkai.dto.WorkflowRequest;
import com.checkai.entity.Task;
import com.checkai.mapper.TaskMapper;
import com.checkai.util.ShortIdUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class WorkflowService {

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private TaskMapper taskMapper;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${checkai.workflow.api-url}")
    private String workflowApiUrl;

    @Value("${checkai.workflow.bot-id}")
    private String botId;

    @Value("${checkai.workflow.authorization}")
    private String authorization;

    @Value("${checkai.langchainAgentApi}")
    private String langchainAgentApi;

    private static final int BATCH_SIZE = 10;

    public String processExcelData(ExcelData excelData, String userId) throws Exception {
        // 生成任务ID
        String taskId = ShortIdUtil.generateShortId(); // 生成8位短ID

        // 检查数据行数并拆分批次
        int totalRows = excelData.getExcelBase().size();
        int totalBatches = (int) Math.ceil((double) totalRows / BATCH_SIZE);
        List<ExcelData> splitDataList = splitExcelData(excelData, totalBatches);

        // 设置超时时间（当前时间+8分钟）
        Date currentTime = new Date();
        Date timeoutTime = new Date(currentTime.getTime() + 8 * 60 * 1000);
        
        // 保存任务到数据库
        Task task = new Task();
        task.setId(ShortIdUtil.generateShortId()); // 生成8位短ID
        task.setTaskId(taskId);
        task.setOriginalTaskId(taskId);
        task.setUserId(userId);
        task.setBatchNumber(0);
        task.setTotalBatches(totalBatches);
        task.setStatus("PENDING");
        task.setDataContent(objectMapper.writeValueAsString(excelData));
        task.setCreateTime(currentTime);
        task.setUpdateTime(currentTime);
        task.setTimeoutTime(timeoutTime);
        task.setProgress(0);
        task.setTotalProgress(totalBatches);
        taskMapper.insert(task);

        // 顺序发送请求（一个一个发送，等待回调成功后再发送下一个）
        for (int i = 0; i < splitDataList.size(); i++) {
            ExcelData splitData = splitDataList.get(i);
            String batchTaskId = taskId + "_batch_" + (i + 1);

            // 发送请求到工作流，最多重试3次
            int retryCount = 0;
            int maxRetries = 3;
            boolean sendSuccess = false;
            
            while (!sendSuccess && retryCount < maxRetries) {
                try {
                    // 发送请求到工作流
                    sendWorkflowRequest(splitData, batchTaskId, taskId);
                    sendSuccess = true;
                    System.out.println("批次发送成功 - taskId: " + taskId + ", batchNumber: " + (i + 1));
                } catch (Exception e) {
                    retryCount++;
                    System.out.println("批次发送失败，正在重试 - taskId: " + taskId + ", batchNumber: " + (i + 1) + ", 重试次数: " + retryCount + ", 错误: " + e.getMessage());
                    
                    // 如果是最后一次重试，更新任务状态为失败
                    if (retryCount >= maxRetries) {
                        Task updateTask = new Task();
                        updateTask.setStatus("FAILED");
                        updateTask.setUpdateTime(new Date());
                        taskMapper.update(updateTask, new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Task>().eq("task_id", taskId));
                        throw new Exception("批次发送失败，已达到最大重试次数 - batchNumber: " + (i + 1) + ", 错误: " + e.getMessage());
                    }
                    
                    // 等待3秒后重试
                    try {
                        Thread.sleep(3000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
            
            // 等待2秒，确保请求按顺序处理
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        return taskId;
    }

    private List<ExcelData> splitExcelData(ExcelData excelData, int totalBatches) {
        List<ExcelData> splitDataList = new ArrayList<>();
        List<Map<String, String>> excelBase = excelData.getExcelBase();
        List<List<String>> excelPull = excelData.getExcelPull();
        List<List<String>> excelPush = excelData.getExcelPush();
        
        int totalRows = excelBase.size();
        System.out.println("开始拆分数据 - 总行数: " + totalRows + ", 总批次数: " + totalBatches);
        
        for (int i = 0; i < totalBatches; i++) {
            int startIndex = i * BATCH_SIZE;
            int endIndex = Math.min((i + 1) * BATCH_SIZE, totalRows);
            
            if (startIndex >= totalRows) {
                break;
            }
            
            System.out.println("批次 " + (i + 1) + " - 开始索引: " + startIndex + ", 结束索引: " + endIndex + ", 数据条数: " + (endIndex - startIndex));
            
            ExcelData splitData = new ExcelData();
            splitData.setExcelBase(excelBase.subList(startIndex, endIndex));
            
            // 处理pull和push数据（如果有）
            if (excelPull != null && !excelPull.isEmpty()) {
                splitData.setExcelPull(excelPull.subList(startIndex, Math.min(endIndex, excelPull.size())));
            }
            
            if (excelPush != null && !excelPush.isEmpty()) {
                splitData.setExcelPush(excelPush.subList(startIndex, Math.min(endIndex, excelPush.size())));
            }
            
            splitDataList.add(splitData);
        }
        
        System.out.println("数据拆分完成 - 实际批次数: " + splitDataList.size());
        
        // 验证数据总量
        int totalProcessedRows = 0;
        for (int i = 0; i < splitDataList.size(); i++) {
            int batchRows = splitDataList.get(i).getExcelBase().size();
            totalProcessedRows += batchRows;
            System.out.println("批次 " + (i + 1) + " 实际数据条数: " + batchRows);
        }
        System.out.println("总处理数据条数: " + totalProcessedRows + ", 原始数据条数: " + totalRows);
        
        if (totalProcessedRows != totalRows) {
            System.out.println("警告: 数据拆分不完整，缺失 " + (totalRows - totalProcessedRows) + " 条数据");
        }
        
        return splitDataList;
    }

    private void sendWorkflowRequest(ExcelData excelData, String batchTaskId, String taskId) throws Exception {
        // 构建请求数据
        Map<String, Object> requestData = new HashMap<>();
        
        // 构建additional_messages
        List<Map<String, Object>> additionalMessages = new ArrayList<>();
        Map<String, Object> message = new HashMap<>();
        message.put("role", "joe");
        message.put("content_type", "text");
        
        // 构建taskId数据
        List<String> taskIdList = new ArrayList<>();
        Map<String, String> taskIdMap = new HashMap<>();
        taskIdMap.put("taskId", batchTaskId);
        taskIdList.add(objectMapper.writeValueAsString(taskIdMap));
        
        // 构建excel_base数据
        List<String> excelBaseList = new ArrayList<>();
        for (Map<String, String> item : excelData.getExcelBase()) {
            excelBaseList.add(objectMapper.writeValueAsString(item));
        }
        
        // 构建excel_pull数据
        List<String> excelPullList = new ArrayList<>();
        if (excelData.getExcelPull() != null && !excelData.getExcelPull().isEmpty()) {
            for (List<String> item : excelData.getExcelPull()) {
                excelPullList.add(objectMapper.writeValueAsString(item));
            }
        }
        
        // 构建excel_push数据
        List<String> excelPushList = new ArrayList<>();
        if (excelData.getExcelPush() != null && !excelData.getExcelPush().isEmpty()) {
            for (List<String> item : excelData.getExcelPush()) {
                excelPushList.add(objectMapper.writeValueAsString(item));
            }
        }
        
        // 构建data数据
        Map<String, Object> data = new HashMap<>();
        data.put("taskId", taskIdList);
        data.put("excel_base", excelBaseList);
        data.put("excel_pull", excelPullList);
        data.put("excel_push", excelPushList);
        
        // 构建content数据
        Map<String, Object> contentMap = new HashMap<>();
        contentMap.put("data", data);
        String content = objectMapper.writeValueAsString(contentMap);
        
        message.put("content", content);
        additionalMessages.add(message);
        
        // 设置请求参数
        requestData.put("additional_messages", additionalMessages);
        requestData.put("workflow_id", "7595443144305852425");
        requestData.put("parameters", new HashMap<>());

        // 设置请求头
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", authorization);

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestData, headers);

        // 发送请求
        ResponseEntity<String> response = restTemplate.postForEntity("https://api.coze.cn/v1/workflows/chat", requestEntity, String.class);
        
        // 处理响应
        if (response.getStatusCode().is2xxSuccessful()) {
            // 响应成功，不需要更新任务状态（等待回调）
        } else {
            // 响应失败，更新任务状态为失败
            Task updateTask = new Task();
            updateTask.setStatus("FAILED");
            updateTask.setUpdateTime(new Date());
            taskMapper.update(updateTask, new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Task>().eq("task_id", taskId));
            throw new Exception("Workflow request failed: " + response.getStatusCode());
        }
    }

    public Map<String, Object> processExcelWithLangChain(org.springframework.web.multipart.MultipartFile file, String userId) throws Exception {
        // 生成任务ID
        String taskId = ShortIdUtil.generateShortId();

        // 构建multipart/form-data请求
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        // 构建请求体
        org.springframework.util.MultiValueMap<String, Object> body = new org.springframework.util.LinkedMultiValueMap<>();
        // 添加文件 - 使用ByteArrayResource代替InputStreamResource，因为ByteArrayResource可以多次读取
        byte[] fileBytes = file.getBytes();
        body.add("file", new org.springframework.core.io.ByteArrayResource(fileBytes) {
            @Override
            public String getFilename() {
                return file.getOriginalFilename();
            }
        });

        HttpEntity<org.springframework.util.MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        // 发送请求到LangChain Agent API
        ResponseEntity<String> response = restTemplate.postForEntity(langchainAgentApi, requestEntity, String.class);

        // 处理响应
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new Exception("LangChain Agent API request failed: " + response.getStatusCode());
        }

        // 解析响应结果
        Map<String, Object> responseData = objectMapper.readValue(response.getBody(), Map.class);

        // 保存任务到数据库
        Task task = new Task();
        task.setId(ShortIdUtil.generateShortId());
        task.setTaskId(taskId);
        task.setOriginalTaskId(taskId);
        task.setUserId(userId);
        task.setBatchNumber(0);
        task.setTotalBatches(1);
        task.setStatus("COMPLETED");
        task.setDataContent(response.getBody());
        task.setCreateTime(new Date());
        task.setUpdateTime(new Date());
        task.setTimeoutTime(new Date(System.currentTimeMillis() + 8 * 60 * 1000));
        task.setProgress(100);
        task.setTotalProgress(100);
        taskMapper.insert(task);

        // 构建返回结果
        Map<String, Object> result = new HashMap<>();
        result.put("taskId", taskId);
        result.put("response", responseData);

        return result;
    }
}
