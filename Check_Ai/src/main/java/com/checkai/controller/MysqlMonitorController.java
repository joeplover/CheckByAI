package com.checkai.controller;

import com.checkai.service.MysqlMonitorService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/mysql-monitor")
@Slf4j
public class MysqlMonitorController {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private MysqlMonitorService mysqlMonitorService;

    private static final String CACHE_KEY_PREFIX = "mysql:monitor:";
    private static final String HOTSPOT_SQL_KEY = "mysql:hotspot:sql";
    private static final String SLOW_QUERY_KEY = "mysql:slow:queries";

    /**
     * 1. 获取最新的MySQL状态指标
     */
    @GetMapping("/status/latest")
    public ResponseEntity<Map<String, Object>> getLatestStatus() {
        try {
            Map<String, Object> status = (Map<String, Object>)
                    redisTemplate.opsForValue().get(CACHE_KEY_PREFIX + "status:latest");

            if (status == null) {
                // 如果没有缓存，触发一次收集
                mysqlMonitorService.collectStatusMetrics();
                status = (Map<String, Object>)
                        redisTemplate.opsForValue().get(CACHE_KEY_PREFIX + "status:latest");
            }

            return ResponseEntity.ok(status != null ? status : new HashMap<>());
        } catch (Exception e) {
            log.error("获取MySQL状态失败", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 2. 获取连接状态
     */
    @GetMapping("/connections")
    public ResponseEntity<Map<String, Object>> getConnectionStatus() {
        try {
            Map<String, Object> status = (Map<String, Object>)
                    redisTemplate.opsForValue().get(CACHE_KEY_PREFIX + "status:latest");

            if (status == null) {
                return ResponseEntity.ok(Map.of(
                        "message", "暂无数据，请等待监控收集"
                ));
            }

            // 提取连接相关指标
            Map<String, Object> connections = new HashMap<>();
            connections.put("Threads_connected", status.get("Threads_connected"));
            connections.put("Threads_running", status.get("Threads_running"));
            connections.put("Threads_created", status.get("Threads_created"));
            connections.put("Max_used_connections", status.get("Max_used_connections"));

            // 计算连接使用率
            try {
                int current = Integer.parseInt((String) status.get("Threads_connected"));
                int maxUsed = Integer.parseInt((String) status.get("Max_used_connections"));
                double usageRate = maxUsed > 0 ? (double) current / maxUsed * 100 : 0;
                connections.put("connection_usage_rate", String.format("%.2f%%", usageRate));
            } catch (Exception e) {
                connections.put("connection_usage_rate", "N/A");
            }

            return ResponseEntity.ok(connections);
        } catch (Exception e) {
            log.error("获取连接状态失败", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 3. 获取SQL执行统计
     */
    @GetMapping("/sql-stats")
    public ResponseEntity<Map<String, Object>> getSqlStatistics() {
        try {
            Map<String, Object> status = (Map<String, Object>)
                    redisTemplate.opsForValue().get(CACHE_KEY_PREFIX + "status:latest");

            if (status == null) {
                return ResponseEntity.ok(Map.of("message", "暂无数据"));
            }

            Map<String, Object> sqlStats = new HashMap<>();

            // 获取命令统计
            Map<String, Object> commandStats = (Map<String, Object>)
                    status.get("command_statistics");

            if (commandStats != null) {
                sqlStats.put("command_statistics", commandStats);

                // 计算总查询数
                long totalQueries = commandStats.values().stream()
                        .map(value -> {
                            try {
                                return Long.parseLong(value.toString());
                            } catch (Exception e) {
                                return 0L;
                            }
                        })
                        .mapToLong(Long::longValue)
                        .sum();
                sqlStats.put("total_queries", totalQueries);
            }

            // 获取慢查询数量
            Object slowCount = redisTemplate.opsForValue().get("mysql:slow:count");
            sqlStats.put("slow_query_count", slowCount != null ? slowCount : 0);

            return ResponseEntity.ok(sqlStats);
        } catch (Exception e) {
            log.error("获取SQL统计失败", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 4. 获取热点SQL（TOP N）
     */
    @GetMapping("/hotspot-sql")
    public ResponseEntity<List<Map<String, Object>>> getHotspotSql(
            @RequestParam(defaultValue = "10") int limit) {
        try {
            // 从有序集合获取排名前N的热点SQL
            Set<Object> hotspotSqls = redisTemplate.opsForZSet()
                    .reverseRange(HOTSPOT_SQL_KEY, 0, limit - 1);

            List<Map<String, Object>> result = new ArrayList<>();

            if (hotspotSqls != null) {
                for (Object sql : hotspotSqls) {
                    Double score = redisTemplate.opsForZSet()
                            .score(HOTSPOT_SQL_KEY, sql);

                    Map<String, Object> sqlInfo = new HashMap<>();
                    sqlInfo.put("sql", sql);
                    sqlInfo.put("execution_count", score != null ? score.longValue() : 0);

                    // 可以添加更多详细信息
                    result.add(sqlInfo);
                }
            }

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("获取热点SQL失败", e);
            return ResponseEntity.internalServerError()
                    .body(Collections.singletonList(
                            Map.of("error", e.getMessage())
                    ));
        }
    }

    /**
     * 5. 获取慢查询列表
     */
    @GetMapping("/slow-queries")
    public ResponseEntity<List<Map<String, Object>>> getSlowQueries(
            @RequestParam(defaultValue = "20") int limit) {
        try {
            // 使用模式匹配获取所有慢查询key
            Set<String> keys = redisTemplate.keys(SLOW_QUERY_KEY + ":*");
            List<Map<String, Object>> slowQueries = new ArrayList<>();

            if (keys != null) {
                int count = 0;
                for (String key : keys) {
                    if (count >= limit) break;

                    Map<String, Object> query = (Map<String, Object>)
                            redisTemplate.opsForValue().get(key);
                    if (query != null) {
                        slowQueries.add(query);
                        count++;
                    }
                }

                // 按时间倒序排序
                slowQueries.sort((a, b) -> {
                    try {
                        java.sql.Timestamp t1 = (java.sql.Timestamp) a.get("start_time");
                        java.sql.Timestamp t2 = (java.sql.Timestamp) b.get("start_time");
                        return t2.compareTo(t1);
                    } catch (Exception e) {
                        return 0;
                    }
                });
            }

            return ResponseEntity.ok(slowQueries);
        } catch (Exception e) {
            log.error("获取慢查询失败", e);
            return ResponseEntity.internalServerError()
                    .body(Collections.singletonList(
                            Map.of("error", e.getMessage())
                    ));
        }
    }

    /**
     * 6. 手动触发监控数据收集
     */
    @PostMapping("/collect/manual")
    public ResponseEntity<Map<String, Object>> triggerManualCollect() {
        try {
            // 触发状态收集
            mysqlMonitorService.collectStatusMetrics();

            // 触发热点SQL收集
            mysqlMonitorService.collectHotspotSQL();

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "监控数据收集已触发",
                    "timestamp", new Date()
            ));
        } catch (Exception e) {
            log.error("手动触发收集失败", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of(
                            "success", false,
                            "error", e.getMessage()
                    ));
        }
    }

    /**
     * 7. 获取监控数据统计信息
     */
    @GetMapping("/statistics")
    public ResponseEntity<Map<String, Object>> getMonitorStatistics() {
        try {
            Map<String, Object> stats = new HashMap<>();

            // 状态数据数量
            Long statusCount = (long) redisTemplate.keys(CACHE_KEY_PREFIX + "status:*").size();
            stats.put("status_data_count", statusCount != null ? statusCount : 0);

            // 热点SQL数量
            Long hotspotCount = redisTemplate.opsForZSet().size(HOTSPOT_SQL_KEY);
            stats.put("hotspot_sql_count", hotspotCount != null ? hotspotCount : 0);

            // 慢查询数量
            Set<String> slowKeys = redisTemplate.keys(SLOW_QUERY_KEY + ":*");
            stats.put("slow_query_count", slowKeys != null ? slowKeys.size() : 0);

            // 最新更新时间
            Object latestStatus = redisTemplate.opsForValue().get(CACHE_KEY_PREFIX + "status:latest");
            if (latestStatus != null) {
                // 如果status里有timestamp，可以显示
                stats.put("last_update", new Date());
            }

            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("获取监控统计失败", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage()));
        }
    }
}