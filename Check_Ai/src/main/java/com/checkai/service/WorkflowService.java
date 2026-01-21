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
        String originalTaskId = ShortIdUtil.generateShortId(); // 生成8位短ID
        List<ExcelData> splitDataList = new ArrayList<>();

        // 检查数据是否需要拆分
        if (excelData.getExcelBase().size() > BATCH_SIZE) {
            splitDataList = new ExcelService().splitExcelData(excelData, BATCH_SIZE);
        } else {
            splitDataList.add(excelData);
        }

        int totalBatches = splitDataList.size();

        // 分批处理数据
        for (int i = 0; i < splitDataList.size(); i++) {
            ExcelData splitData = splitDataList.get(i);
            String taskId = originalTaskId + "_batch_" + (i + 1);

            // 设置超时时间（当前时间+8分钟）
            Date currentTime = new Date();
            Date timeoutTime = new Date(currentTime.getTime() + 8 * 60 * 1000);
            
            // 保存任务到数据库
            Task task = new Task();
            task.setId(ShortIdUtil.generateShortId()); // 生成8位短ID
            task.setTaskId(taskId);
            task.setOriginalTaskId(originalTaskId);
            task.setUserId(userId);
            task.setBatchNumber(i + 1);
            task.setTotalBatches(totalBatches);
            task.setStatus("PENDING");
            task.setDataContent(objectMapper.writeValueAsString(splitData));
            task.setCreateTime(currentTime);
            task.setUpdateTime(currentTime);
            task.setTimeoutTime(timeoutTime);
            // 设置进度信息
            task.setProgress(i + 1);
            task.setTotalProgress(totalBatches);
            taskMapper.insert(task);

            // 发送请求到工作流
            sendWorkflowRequest(splitData, taskId);
        }

        return originalTaskId;
    }

    private void sendWorkflowRequest(ExcelData excelData, String taskId) throws Exception {
        // 构建请求数据
        WorkflowRequest workflowRequest = new WorkflowRequest();
        workflowRequest.setBot_id(botId);
        workflowRequest.setUser_id(taskId);
        workflowRequest.setStream(false);
        workflowRequest.setAuto_save_history(true);

        // 构建additional_messages
        List<WorkflowRequest.AdditionalMessage> additionalMessages = new ArrayList<>();
        WorkflowRequest.AdditionalMessage message = new WorkflowRequest.AdditionalMessage();
        message.setRole("user");
        
        // 转换ExcelData为符合Coze API要求的格式
        Map<String, Object> cozeData = new HashMap<>();
        
        // 添加taskId字段
        List<Map<String, String>> taskIdList = new ArrayList<>();
        Map<String, String> taskIdMap = new HashMap<>();
        taskIdMap.put("taskId", taskId);
        taskIdList.add(taskIdMap);
        cozeData.put("taskId", taskIdList);
        
        // 转换字段名为下划线格式
        cozeData.put("excel_base", excelData.getExcelBase());
        cozeData.put("excel_pull", excelData.getExcelPull());
        cozeData.put("excel_push", excelData.getExcelPush());
        
        // 创建请求内容
        Map<String, Object> contentMap = new HashMap<>();
        contentMap.put("data", cozeData);
        String content = objectMapper.writeValueAsString(contentMap);
        
        message.setContent(content);
        message.setContent_type("text");
        
        additionalMessages.add(message);
        workflowRequest.setAdditional_messages(additionalMessages);

        // 设置请求头
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", authorization);

        HttpEntity<WorkflowRequest> requestEntity = new HttpEntity<>(workflowRequest, headers);

        // 发送请求
        ResponseEntity<String> response = restTemplate.postForEntity(workflowApiUrl, requestEntity, String.class);
        
        // 处理响应
        if (response.getStatusCode().is2xxSuccessful()) {
            // 更新任务状态为已发送
            Task task = new Task();
            task.setTaskId(taskId);
            task.setStatus("SENT");
            task.setUpdateTime(new Date());
            taskMapper.updateById(task);
        } else {
            // 更新任务状态为失败
            Task task = new Task();
            task.setTaskId(taskId);
            task.setStatus("FAILED");
            task.setUpdateTime(new Date());
            taskMapper.updateById(task);
            throw new Exception("Workflow request failed: " + response.getStatusCode());
        }
    }
}
