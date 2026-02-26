package com.checkai.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 通过 RabbitMQ 传递的任务处理消息
 */
@Data
public class TaskProcessMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 消息ID，用于幂等和链路追踪
     */
    private String messageId;

    /**
     * 业务任务ID（对应 task.task_id）
     */
    private String taskId;

    /**
     * 用户ID
     */
    private String userId;

    /**
     * Excel 解析后的数据载荷
     */
    private ExcelData excelData;

    /**
     * 消息创建时间
     */
    private Date createdAt;

    /**
     * 链路追踪ID（可与 messageId 相同）
     */
    private String traceId;
}


