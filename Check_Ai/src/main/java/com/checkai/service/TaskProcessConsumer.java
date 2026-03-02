package com.checkai.service;

import com.checkai.config.RabbitMQConfig;
import com.checkai.dto.TaskProcessMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class TaskProcessConsumer {

    private static final Logger logger = LoggerFactory.getLogger(TaskProcessConsumer.class);

    @Autowired
    private WorkflowService workflowService;

    @Autowired
    private IdempotencyService idempotencyService;

    @RabbitListener(queues = RabbitMQConfig.CHECKAI_TASK_PROCESS_QUEUE)
    public void handleTaskProcess(TaskProcessMessage message) {
        String taskId = message.getTaskId();
        String messageId = message.getMessageId();
        
        try {
            if (idempotencyService.isMessageConsumed(messageId)) {
                logger.warn("消息已消费，跳过 - taskId={}, messageId={}", taskId, messageId);
                return;
            }
            
            if (!idempotencyService.markMessageConsumed(messageId)) {
                logger.warn("消息消费标记失败，可能被其他消费者处理 - taskId={}, messageId={}", taskId, messageId);
                return;
            }
            
            logger.info("开始处理任务消息，taskId={}, messageId={}", taskId, messageId);
            workflowService.processExcelTask(message.getExcelData(), taskId);
            logger.info("任务消息处理完成，taskId={}, messageId={}", taskId, messageId);
        } catch (Exception e) {
            logger.error("任务消息处理失败，将丢入DLQ，taskId={}, messageId={}, error={}",
                    taskId, messageId, e.getMessage(), e);
            throw new AmqpRejectAndDontRequeueException("处理任务消息失败，taskId=" + taskId, e);
        }
    }
}


