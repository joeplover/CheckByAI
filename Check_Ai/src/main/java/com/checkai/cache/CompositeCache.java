package com.checkai.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;

import java.util.concurrent.Callable;

public class CompositeCache implements Cache {

    private static final Logger logger = LoggerFactory.getLogger(CompositeCache.class);

    private final Cache l1Cache;
    private final Cache l2Cache;
    private final String name;

    public CompositeCache(String name, Cache l1Cache, Cache l2Cache) {
        this.name = name;
        this.l1Cache = l1Cache;
        this.l2Cache = l2Cache;
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public Object getNativeCache() {
        return this;
    }

    @Override
    public ValueWrapper get(Object key) {
        ValueWrapper value = l1Cache.get(key);
        if (value != null) {
            logger.debug("L1缓存命中 - cache={}, key={}", name, key);
            return value;
        }

        value = l2Cache.get(key);
        if (value != null) {
            logger.debug("L2缓存命中，回填L1 - cache={}, key={}", name, key);
            l1Cache.put(key, value.get());
            return value;
        }

        logger.debug("缓存未命中 - cache={}, key={}", name, key);
        return null;
    }

    @Override
    public <T> T get(Object key, Class<T> type) {
        ValueWrapper wrapper = get(key);
        if (wrapper != null) {
            return type.cast(wrapper.get());
        }
        return null;
    }

    @Override
    public <T> T get(Object key, Callable<T> valueLoader) {
        ValueWrapper value = get(key);
        if (value != null) {
            return (T) value.get();
        }

        try {
            T loadedValue = valueLoader.call();
            put(key, loadedValue);
            return loadedValue;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void put(Object key, Object value) {
        logger.debug("写入两级缓存 - cache={}, key={}", name, key);
        l1Cache.put(key, value);
        l2Cache.put(key, value);
    }

    @Override
    public void evict(Object key) {
        logger.debug("清除两级缓存 - cache={}, key={}", name, key);
        l1Cache.evict(key);
        l2Cache.evict(key);
    }

    @Override
    public boolean evictIfPresent(Object key) {
        boolean l1Evicted = l1Cache.evictIfPresent(key);
        boolean l2Evicted = l2Cache.evictIfPresent(key);
        return l1Evicted || l2Evicted;
    }

    @Override
    public void clear() {
        logger.debug("清空两级缓存 - cache={}", name);
        l1Cache.clear();
        l2Cache.clear();
    }

    @Override
    public boolean invalidate() {
        boolean l1Invalidated = l1Cache.invalidate();
        boolean l2Invalidated = l2Cache.invalidate();
        return l1Invalidated || l2Invalidated;
    }

    public Cache getL1Cache() {
        return l1Cache;
    }

    public Cache getL2Cache() {
        return l2Cache;
    }
}
