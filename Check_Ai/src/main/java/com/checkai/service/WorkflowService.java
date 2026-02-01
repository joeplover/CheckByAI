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

    private static final int BATCH_SIZE = 5;

    public String processExcelData(ExcelData excelData, String userId) throws Exception {
        String taskId = ShortIdUtil.generateShortId(); // 生成8位短ID
        List<ExcelData> splitDataList = new ArrayList<>();

        // 检查数据是否需要拆分
        int totalRows = excelData.getExcelBase().size();
        int batchSize = 5;
        int totalBatches = 0;
        
        if (totalRows > 5) {
            if (totalRows <= 10) {
                totalBatches = 2;
                batchSize = 5;
            } else if (totalRows <= 15) {
                totalBatches = 3;
                batchSize = 5;
            } else if (totalRows <= 20) {
                totalBatches = 4;
                batchSize = 5;
            }
            
            // 拆分数据
            splitDataList = splitExcelData(excelData, batchSize, totalBatches);
        } else {
            splitDataList.add(excelData);
            totalBatches = 1;
        }

        // 设置超时时间（当前时间+8分钟）
        Date currentTime = new Date();
        Date timeoutTime = new Date(currentTime.getTime() + 8 * 60 * 1000);
        
        // 只保存一条任务到数据库
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

        // 分批处理数据
        for (int i = 0; i < splitDataList.size(); i++) {
            ExcelData splitData = splitDataList.get(i);
            String batchTaskId = taskId + "_batch_" + (i + 1);

            // 发送请求到工作流
            sendWorkflowRequest(splitData, batchTaskId, taskId);
        }

        return taskId;
    }

    private List<ExcelData> splitExcelData(ExcelData excelData, int batchSize, int totalBatches) {
        List<ExcelData> splitDataList = new ArrayList<>();
        List<Map<String, String>> excelBase = excelData.getExcelBase();
        List<List<String>> excelPull = excelData.getExcelPull();
        List<List<String>> excelPush = excelData.getExcelPush();
        
        for (int i = 0; i < totalBatches; i++) {
            int startIndex = i * batchSize;
            int endIndex = Math.min((i + 1) * batchSize, excelBase.size());
            
            if (startIndex >= excelBase.size()) {
                break;
            }
            
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
        
        return splitDataList;
    }

    private void sendWorkflowRequest(ExcelData excelData, String batchTaskId, String mainTaskId) throws Exception {
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
        taskIdMap.put("mainTaskId", mainTaskId);
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
            taskMapper.update(updateTask, new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Task>().eq("task_id", mainTaskId));
            throw new Exception("Workflow request failed: " + response.getStatusCode());
        }
    }
}
