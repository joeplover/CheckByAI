package com.checkai.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class RabbitMQConfig {

    public static final String CHECKAI_TASK_EXCHANGE = "checkai.task.exchange";
    public static final String CHECKAI_TASK_PROCESS_QUEUE = "checkai.task.process.q";
    public static final String CHECKAI_TASK_PROCESS_DLQ = "checkai.task.process.dlq";

    public static final String ROUTING_KEY_TASK_PROCESS = "task.process";
    public static final String ROUTING_KEY_TASK_PROCESS_DLQ = "task.process.dlq";

    @Bean
    public TopicExchange checkaiTaskExchange() {
        return new TopicExchange(CHECKAI_TASK_EXCHANGE, true, false);
    }

    @Bean
    public Queue checkaiTaskProcessQueue() {
        Map<String, Object> args = new HashMap<>();
        // 主队列配置死信交换机和路由键
        args.put("x-dead-letter-exchange", CHECKAI_TASK_EXCHANGE);
        args.put("x-dead-letter-routing-key", ROUTING_KEY_TASK_PROCESS_DLQ);
        return new Queue(CHECKAI_TASK_PROCESS_QUEUE, true, false, false, args);
    }

    @Bean
    public Queue checkaiTaskProcessDlq() {
        return new Queue(CHECKAI_TASK_PROCESS_DLQ, true);
    }

    @Bean
    public Binding taskProcessBinding() {
        return BindingBuilder.bind(checkaiTaskProcessQueue())
                .to(checkaiTaskExchange())
                .with(ROUTING_KEY_TASK_PROCESS);
    }

    @Bean
    public Binding taskProcessDlqBinding() {
        return BindingBuilder.bind(checkaiTaskProcessDlq())
                .to(checkaiTaskExchange())
                .with(ROUTING_KEY_TASK_PROCESS_DLQ);
    }

    /**
     * 统一的 JSON 消息转换器：
     * - RabbitTemplate 发送时使用
     * - @RabbitListener 监听容器也会自动使用
     */
    @Bean
    public Jackson2JsonMessageConverter jackson2JsonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         Jackson2JsonMessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        template.setMandatory(true);
        return template;
    }
}


