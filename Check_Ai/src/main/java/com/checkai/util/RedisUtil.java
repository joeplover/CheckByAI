package com.checkai.util;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class RedisUtil {
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    //存数据
    public void set(String key, Object value) {
        redisTemplate.opsForValue().set(key, value);
    }
    //存数据并设置过期时间
    public void set(String key, Object value,long time,TimeUnit unit){
        redisTemplate.opsForValue().set(key, value, time, unit);
    }
    //取数据
    public Object get(String key) {
        return redisTemplate.opsForValue().get(key);
    }
    //删除数据
    public void delete(String key) {
        redisTemplate.delete(key);
    }
    //设置过期时间
    public Boolean expire(String key, long time, TimeUnit unit){
        return redisTemplate.expire(key, time, unit);
    }
    //获取过期时间
    public Long getExpire(String key){
        return redisTemplate.getExpire(key,TimeUnit.SECONDS);
    }
    //判断key是否存在
    public Boolean hasKey(String key){
        return redisTemplate.hasKey(key);
    }

}
