package com.checkai.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.data.redis.cache.RedisCacheManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class CompositeCacheManager implements CacheManager {

    private static final Logger logger = LoggerFactory.getLogger(CompositeCacheManager.class);

    private final CaffeineCacheManager caffeineCacheManager;
    private final RedisCacheManager redisCacheManager;
    private final List<String> cacheNames;

    public CompositeCacheManager(CaffeineCacheManager caffeineCacheManager, RedisCacheManager redisCacheManager) {
        this.caffeineCacheManager = caffeineCacheManager;
        this.redisCacheManager = redisCacheManager;

        List<String> allCacheNames = new ArrayList<>();
        allCacheNames.addAll(caffeineCacheManager.getCacheNames());
        allCacheNames.addAll(redisCacheManager.getCacheNames());
        this.cacheNames = allCacheNames;

        logger.info("复合缓存管理器初始化完成 - L1(Caffeine): {}, L2(Redis): {}", 
                caffeineCacheManager.getCacheNames(), redisCacheManager.getCacheNames());
    }

    @Override
    public Cache getCache(String name) {
        Cache l1Cache = caffeineCacheManager.getCache(name);
        Cache l2Cache = redisCacheManager.getCache(name);

        if (l1Cache == null && l2Cache == null) {
            return null;
        }

        if (l1Cache == null) {
            return l2Cache;
        }

        if (l2Cache == null) {
            return l1Cache;
        }

        return new CompositeCache(name, l1Cache, l2Cache);
    }

    @Override
    public Collection<String> getCacheNames() {
        return this.cacheNames;
    }

    public CaffeineCacheManager getCaffeineCacheManager() {
        return caffeineCacheManager;
    }

    public RedisCacheManager getRedisCacheManager() {
        return redisCacheManager;
    }
}
