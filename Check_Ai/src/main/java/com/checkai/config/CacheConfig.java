package com.checkai.config;

import com.checkai.cache.CompositeCacheManager;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
public class CacheConfig {

    private static final Logger logger = LoggerFactory.getLogger(CacheConfig.class);

    @Value("${cache.caffeine.expire-after-write:300}")
    private long caffeineExpireAfterWrite;

    @Value("${cache.caffeine.maximum-size:1000}")
    private long caffeineMaximumSize;

    @Bean
    public Caffeine<Object, Object> caffeineConfig() {
        return Caffeine.newBuilder()
                .expireAfterWrite(caffeineExpireAfterWrite, TimeUnit.SECONDS)
                .maximumSize(caffeineMaximumSize)
                .recordStats();
    }

    @Bean
    public CaffeineCacheManager caffeineCacheManager(Caffeine<Object, Object> caffeine) {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCaffeine(caffeine);
        logger.info("Caffeine本地缓存管理器初始化完成 - expireAfterWrite={}s, maximumSize={}",
                caffeineExpireAfterWrite, caffeineMaximumSize);
        return cacheManager;
    }

    @Bean
    public RedisCacheManager redisCacheManager(RedisConnectionFactory factory) {
        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();
        
        cacheConfigurations.put("tasksByUser", createCacheConfig(30));
        cacheConfigurations.put("taskResultsByUserAndTask", createCacheConfig(15));
        cacheConfigurations.put("originalTaskResultsByUserAndOriginal", createCacheConfig(15));

        RedisCacheManager cacheManager = RedisCacheManager.builder(factory)
                .cacheDefaults(createCacheConfig(60))
                .withInitialCacheConfigurations(cacheConfigurations)
                .build();
        
        logger.info("Redis分布式缓存管理器初始化完成");
        return cacheManager;
    }

    @Bean
    @Primary
    public CacheManager compositeCacheManager(CaffeineCacheManager caffeineCacheManager, 
                                              RedisCacheManager redisCacheManager) {
        return new CompositeCacheManager(caffeineCacheManager, redisCacheManager);
    }

    private RedisCacheConfiguration createCacheConfig(long ttlSeconds) {
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofSeconds(ttlSeconds))
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer()))
                .disableCachingNullValues();
    }
}
