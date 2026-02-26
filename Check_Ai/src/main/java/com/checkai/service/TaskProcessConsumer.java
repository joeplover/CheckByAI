package com.checkai.service;

import com.checkai.config.RabbitMQConfig;
import com.checkai.dto.TaskProcessMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 任务处理消费者：从 RabbitMQ 消费 TaskProcessMessage 并触发实际工作流处理
 */
@Component
public class TaskProcessConsumer {

    private static final Logger logger = LoggerFactory.getLogger(TaskProcessConsumer.class);

    private final WorkflowService workflowService;

    public TaskProcessConsumer(WorkflowService workflowService) {
        this.workflowService = workflowService;
    }

    @RabbitListener(queues = RabbitMQConfig.CHECKAI_TASK_PROCESS_QUEUE)
    public void handleTaskProcess(TaskProcessMessage message) {
        String taskId = message.getTaskId();
        String messageId = message.getMessageId();
        try {
            logger.info("开始处理任务消息，taskId={}, messageId={}", taskId, messageId);
            workflowService.processExcelTask(message.getExcelData(), taskId);
            logger.info("任务消息处理完成，taskId={}, messageId={}", taskId, messageId);
        } catch (Exception e) {
            logger.error("任务消息处理失败，将丢入DLQ，taskId={}, messageId={}, error={}",
                    taskId, messageId, e.getMessage(), e);
            // 抛出 AmqpRejectAndDontRequeueException，触发 x-dead-letter 进入 DLQ
            throw new AmqpRejectAndDontRequeueException("处理任务消息失败，taskId=" + taskId, e);
        }
    }
}


