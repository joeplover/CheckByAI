package com.checkai.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {
    @Bean
    /**
     * 配置自定义的RedisTemplate
     *
     * @param factory Redis连接工厂
     * @return 配置好序列化方式的RedisTemplate实例
     */
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        // 创建RedisTemplate实例
        RedisTemplate<String,Object> redisTemplate = new RedisTemplate<>();
        // 设置连接工厂
        redisTemplate.setConnectionFactory(factory);

        //设置key的序列化方式为String
        //使用StringRedisSeerializer确保key以字符串形式存储
        redisTemplate.setKeySerializer(new StringRedisSerializer());
        redisTemplate.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        //设置hash key的序列化方式为String
        redisTemplate.setHashKeySerializer(new StringRedisSerializer());

        //设置hash value的序列化方式为JSON格式
        redisTemplate.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());

        //初始化Redistemplate,使配置生效
        redisTemplate.afterPropertiesSet();
        return redisTemplate;
    }
}























