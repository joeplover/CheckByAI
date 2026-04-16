package com.checkai.controller;

import com.checkai.service.MysqlMonitorService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.zset.Tuple;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/mysql-monitor")
@Slf4j
public class MysqlMonitorController {

    private static final String CACHE_KEY_PREFIX = "mysql:monitor:";
    private static final String HOTSPOT_SQL_KEY = "mysql:hotspot:sql";
    private static final String SLOW_QUERY_KEY = "mysql:slow:queries";

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private MysqlMonitorService mysqlMonitorService;

    @Autowired
    private RedisConnectionFactory redisConnectionFactory;

    @Autowired
    private ObjectMapper objectMapper;

    @GetMapping("/status/latest")
    public ResponseEntity<Map<String, Object>> getLatestStatus() {
        try {
            Map<String, Object> status = (Map<String, Object>) redisTemplate.opsForValue()
                    .get(CACHE_KEY_PREFIX + "status:latest");

            if (status == null) {
                mysqlMonitorService.collectStatusMetrics();
                status = (Map<String, Object>) redisTemplate.opsForValue()
                        .get(CACHE_KEY_PREFIX + "status:latest");
            }

            return ResponseEntity.ok(status != null ? status : new HashMap<>());
        } catch (Exception e) {
            log.error("Failed to get MySQL status", e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/connections")
    public ResponseEntity<Map<String, Object>> getConnectionStatus() {
        try {
            Map<String, Object> status = (Map<String, Object>) redisTemplate.opsForValue()
                    .get(CACHE_KEY_PREFIX + "status:latest");
            if (status == null) {
                return ResponseEntity.ok(Map.of("message", "No monitor data available"));
            }

            Map<String, Object> connections = new HashMap<>();
            connections.put("Threads_connected", status.get("Threads_connected"));
            connections.put("Threads_running", status.get("Threads_running"));
            connections.put("Threads_created", status.get("Threads_created"));
            connections.put("Max_used_connections", status.get("Max_used_connections"));

            try {
                int current = Integer.parseInt(String.valueOf(status.get("Threads_connected")));
                int maxUsed = Integer.parseInt(String.valueOf(status.get("Max_used_connections")));
                double usageRate = maxUsed > 0 ? (double) current / maxUsed * 100 : 0;
                connections.put("connection_usage_rate", String.format("%.2f%%", usageRate));
            } catch (Exception ignored) {
                connections.put("connection_usage_rate", "N/A");
            }

            return ResponseEntity.ok(connections);
        } catch (Exception e) {
            log.error("Failed to get MySQL connection status", e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/sql-stats")
    public ResponseEntity<Map<String, Object>> getSqlStatistics() {
        try {
            Map<String, Object> status = (Map<String, Object>) redisTemplate.opsForValue()
                    .get(CACHE_KEY_PREFIX + "status:latest");
            if (status == null) {
                return ResponseEntity.ok(Map.of("message", "No monitor data available"));
            }

            Map<String, Object> sqlStats = new HashMap<>();
            Map<String, Object> commandStats = (Map<String, Object>) status.get("command_statistics");
            if (commandStats != null) {
                sqlStats.put("command_statistics", commandStats);
                long totalQueries = commandStats.values().stream()
                        .map(value -> {
                            try {
                                return Long.parseLong(String.valueOf(value));
                            } catch (Exception ignored) {
                                return 0L;
                            }
                        })
                        .mapToLong(Long::longValue)
                        .sum();
                sqlStats.put("total_queries", totalQueries);
            }

            Object slowCount = redisTemplate.opsForValue().get("mysql:slow:count");
            sqlStats.put("slow_query_count", slowCount != null ? slowCount : 0);
            return ResponseEntity.ok(sqlStats);
        } catch (Exception e) {
            log.error("Failed to get SQL statistics", e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/hotspot-sql")
    public ResponseEntity<List<Map<String, Object>>> getHotspotSql(
            @RequestParam(defaultValue = "10") int limit) {
        try {
            List<Map<String, Object>> result = new ArrayList<>();
            byte[] rawKey = HOTSPOT_SQL_KEY.getBytes(StandardCharsets.UTF_8);

            try (RedisConnection connection = redisConnectionFactory.getConnection()) {
                Set<Tuple> tuples =
                        connection.zRevRangeWithScores(rawKey, 0, Math.max(0, limit - 1));
                if (tuples != null) {
                    for (Tuple tuple : tuples) {
                        String sql = decodeRedisMember(tuple.getValue());
                        Double score = tuple.getScore();
                        Map<String, Object> sqlInfo = new HashMap<>();
                        sqlInfo.put("sql", sql);
                        sqlInfo.put("execution_count", score != null ? score.longValue() : 0L);
                        result.add(sqlInfo);
                    }
                }
            }

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Failed to get hotspot SQL", e);
            return ResponseEntity.internalServerError()
                    .body(Collections.singletonList(Map.of("error", e.getMessage())));
        }
    }

    @GetMapping("/slow-queries")
    public ResponseEntity<List<Map<String, Object>>> getSlowQueries(
            @RequestParam(defaultValue = "20") int limit) {
        try {
            Set<String> keys = redisTemplate.keys(SLOW_QUERY_KEY + ":*");
            List<Map<String, Object>> slowQueries = new ArrayList<>();

            if (keys != null) {
                int count = 0;
                for (String key : keys) {
                    if (count >= limit) {
                        break;
                    }
                    Map<String, Object> query = (Map<String, Object>) redisTemplate.opsForValue().get(key);
                    if (query != null) {
                        slowQueries.add(query);
                        count++;
                    }
                }
            }

            slowQueries.sort((a, b) -> {
                try {
                    java.sql.Timestamp t1 = (java.sql.Timestamp) a.get("start_time");
                    java.sql.Timestamp t2 = (java.sql.Timestamp) b.get("start_time");
                    if (t1 == null || t2 == null) {
                        return 0;
                    }
                    return t2.compareTo(t1);
                } catch (Exception ignored) {
                    return 0;
                }
            });

            return ResponseEntity.ok(slowQueries);
        } catch (Exception e) {
            log.error("Failed to get slow queries", e);
            return ResponseEntity.internalServerError()
                    .body(Collections.singletonList(Map.of("error", e.getMessage())));
        }
    }

    @PostMapping("/collect/manual")
    public ResponseEntity<Map<String, Object>> triggerManualCollect() {
        try {
            mysqlMonitorService.collectStatusMetrics();
            mysqlMonitorService.collectHotspotSQL();
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Monitoring data collection triggered",
                    "timestamp", new Date()
            ));
        } catch (Exception e) {
            log.error("Failed to trigger manual monitor collection", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }

    @GetMapping("/statistics")
    public ResponseEntity<Map<String, Object>> getMonitorStatistics() {
        try {
            Map<String, Object> stats = new HashMap<>();

            Set<String> statusKeys = redisTemplate.keys(CACHE_KEY_PREFIX + "status:*");
            stats.put("status_data_count", statusKeys != null ? statusKeys.size() : 0);

            Long hotspotCount = redisTemplate.opsForZSet().size(HOTSPOT_SQL_KEY);
            stats.put("hotspot_sql_count", hotspotCount != null ? hotspotCount : 0);

            Set<String> slowKeys = redisTemplate.keys(SLOW_QUERY_KEY + ":*");
            stats.put("slow_query_count", slowKeys != null ? slowKeys.size() : 0);

            Object latestStatus = redisTemplate.opsForValue().get(CACHE_KEY_PREFIX + "status:latest");
            if (latestStatus != null) {
                stats.put("last_update", new Date());
            }

            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("Failed to get monitor statistics", e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    private String decodeRedisMember(byte[] rawValue) {
        if (rawValue == null || rawValue.length == 0) {
            return "";
        }

        if (rawValue.length >= 4
                && (rawValue[0] & 0xFF) == 0xAC
                && (rawValue[1] & 0xFF) == 0xED
                && (rawValue[2] & 0xFF) == 0x00
                && (rawValue[3] & 0xFF) == 0x05) {
            try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(rawValue))) {
                Object obj = ois.readObject();
                return obj == null ? "" : obj.toString();
            } catch (Exception ignored) {
            }
        }

        try {
            String utf8 = new String(rawValue, StandardCharsets.UTF_8);
            if (!utf8.isBlank()) {
                return utf8;
            }
        } catch (Exception ignored) {
        }

        try {
            return objectMapper.readValue(rawValue, String.class);
        } catch (Exception ignored) {
        }

        return Base64.getEncoder().encodeToString(rawValue);
    }
}
