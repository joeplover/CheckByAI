package com.checkai.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class IdempotencyService {

    private static final Logger logger = LoggerFactory.getLogger(IdempotencyService.class);

    private static final String TASK_DEDUP_PREFIX = "task:dedup:";
    private static final String MSG_CONSUMED_PREFIX = "msg:consumed:";
    private static final String CALLBACK_PROCESSED_PREFIX = "callback:processed:";

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    public boolean tryAcquireTaskLock(String userId, String contentHash) {
        String key = TASK_DEDUP_PREFIX + userId + ":" + contentHash;
        Boolean success = redisTemplate.opsForValue()
                .setIfAbsent(key, "1", 10, TimeUnit.MINUTES);
        if (success != null && success) {
            logger.info("任务去重锁获取成功 - userId={}, hash={}", userId, contentHash);
            return true;
        }
        logger.warn("任务去重锁获取失败，可能存在重复提交 - userId={}, hash={}", userId, contentHash);
        return false;
    }

    public void releaseTaskLock(String userId, String contentHash) {
        String key = TASK_DEDUP_PREFIX + userId + ":" + contentHash;
        redisTemplate.delete(key);
        logger.info("任务去重锁已释放 - userId={}, hash={}", userId, contentHash);
    }

    public boolean isMessageConsumed(String messageId) {
        String key = MSG_CONSUMED_PREFIX + messageId;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    public boolean markMessageConsumed(String messageId) {
        String key = MSG_CONSUMED_PREFIX + messageId;
        Boolean success = redisTemplate.opsForValue()
                .setIfAbsent(key, "1", 24, TimeUnit.HOURS);
        if (success != null && success) {
            logger.info("消息消费标记成功 - messageId={}", messageId);
            return true;
        }
        return false;
    }

    public boolean isCallbackProcessed(String taskId) {
        String key = CALLBACK_PROCESSED_PREFIX + taskId;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    public boolean markCallbackProcessed(String taskId) {
        String key = CALLBACK_PROCESSED_PREFIX + taskId;
        Boolean success = redisTemplate.opsForValue()
                .setIfAbsent(key, "1", 24, TimeUnit.HOURS);
        if (success != null && success) {
            logger.info("回调处理标记成功 - taskId={}", taskId);
            return true;
        }
        return false;
    }

    public String generateContentHash(String content) {
        return String.valueOf(content.hashCode());
    }
}
