package com.checkai;

import com.checkai.entity.User;
import com.checkai.util.RedisUtil;
import org.junit.jupiter.api.Disabled;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

@SpringBootTest
@Disabled("集成测试：依赖外部MySQL/Redis环境，默认在CI/本地不执行。需要时手动开启。")
public class Test {
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    @Autowired
    private RedisUtil redisUtil;

    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    @org.junit.jupiter.api.Test
    public void testGetRedisValues() {
        // 要获取的Redis键列表
        List<String> keys = Arrays.asList(
            "mysql:slow:count",
            "mysql:monitor:status:latest",
            "mysql:monitor:status:1769738253351"
        );
        
        System.out.println("开始从Redis获取数据...");
        
        for (String key : keys) {
            // 使用RedisTemplate获取值
            Object value = redisTemplate.opsForValue().get(key);
            System.out.println("键: " + key + " -> 值: " + value);
            
            // 同时也演示使用RedisUtil工具类获取值
            Object utilValue = redisUtil.get(key);
            System.out.println("通过RedisUtil获取 - 键: " + key + " -> 值: " + utilValue);
            System.out.println("---");
        }
    }
    @org.junit.jupiter.api.Test
    public void testGetHotspotSqlTopKeys() {
        System.out.println("查询热点SQL的顶级键...");

        // 获取热点SQL的有序集合
        String hotspotSqlKey = "mysql:hotspot:sql";
        Set<Object> hotspotSqls = redisTemplate.opsForZSet().reverseRange(hotspotSqlKey, 0, 9); // 获取前10个

        if (hotspotSqls != null && !hotspotSqls.isEmpty()) {
            System.out.println("热点SQL列表 (按执行次数排序):");
            for (Object sql : hotspotSqls) {
                Double score = redisTemplate.opsForZSet().score(hotspotSqlKey, sql);
                System.out.println("- SQL: " + sql + ", 执行次数: " + (score != null ? score.longValue() : 0));

                // 尝试获取对应的详细信息
                String detailKey = "mysql:hotspot:sql:detail:" +
                        org.springframework.util.DigestUtils.md5DigestAsHex(((String) sql).getBytes());
                Object detailData = redisTemplate.opsForHash().entries(detailKey);
                if (detailData != null && !((Map<?, ?>) detailData).isEmpty()) {
                    System.out.println("  详细统计: " + detailData);
                }
                System.out.println("---");
            }
        } else {
            System.out.println("未找到热点SQL数据");
        }
    }

    @org.junit.jupiter.api.Test
    public void jdbcTest(){
        System.out.println("JDBC测试方法");
        List<Map<String, Object>> users = jdbcTemplate.queryForList("SELECT * FROM user;");
        for (Map<String, Object> user : users) {
            System.out.println(user.toString());
        }
//        System.out.println(users.toString());
    }
}
