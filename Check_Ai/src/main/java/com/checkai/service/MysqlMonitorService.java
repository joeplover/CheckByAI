package com.checkai.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class MysqlMonitorService {
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private static final String CACHE_KEY_PREFIX = "mysql:monitor:";
    private static final String HOTSPOT_SQL_KEY= "mysql:hotspot:sql";
    private static final String SLOW_QUERY_KEY= "mysql:slow:queries";
    private static final String CONNECTION_POOL_KEY= "mysql:connections";

//    收集SHOW STATUS数据
    @Scheduled(fixedRate = 60000)//每分钟执行
    public void collectStatusMetrics(){
        try{
            //收集关键状态指标
            Map<String,Object> statusMetrics = new HashMap<>();

            //查询连接相关指标
            List<Map<String, Object>> statusList = jdbcTemplate.queryForList(
                    "SHOW GLOBAL STATUS WHERE variable_name IN" +
                            "('Threads_connected','Threads_running','Threads_created','Max_used_connections')"
            );

            for (Map<String, Object> row : statusList) {
                String variableName = (String) row.get("variable_name");
                String value = (String) row.get("value");
                statusMetrics.put(variableName, value);
            }

            //查询查询次数统计
            List<Map<String, Object>> comStats = jdbcTemplate.queryForList(
                    "SELECT Variable_name, Variable_value " +
                            "FROM performance_schema.global_status " +
                            "WHERE Variable_name LIKE 'Com_%' " +
                            "AND Variable_value > 0 " +
                            "ORDER BY Variable_value DESC LIMIT 10"
            );

            HashMap<Object, Object> comMetrics = new HashMap<>();
            for (Map<String, Object> row : comStats){
                comMetrics.put(
                        row.get("Variable_name"),
                        row.get("Variable_value")
                );
            }
            statusMetrics.put("command_statistics", comMetrics);

            //存储到Redis(热点数据)
            String cacheKey = CACHE_KEY_PREFIX + "status:" + System.currentTimeMillis();
            redisTemplate.opsForValue().set(cacheKey, statusMetrics,1, TimeUnit.HOURS);

            //更新最新状态
            redisTemplate.opsForValue().set(
                    CACHE_KEY_PREFIX + "status:latest",
                    statusMetrics,
                    5, TimeUnit.MINUTES
            );
            log.info("收集MySQL状态指标成功");



        } catch (Exception e) {
            log.error("收集MySQL状态指标失败", e);
        }
    }
    //收集热点SQL(最频繁执行的SQL)
    @Scheduled(fixedRate = 30000)//每30秒执行一次
    public void collectHotspotSQL(){
        try {
            String sql = """
                SELECT 
                    DIGEST_TEXT as sql_digest,
                    COUNT_STAR as execution_count,
                    ROUND(SUM_TIMER_WAIT/1000000000, 2) as total_time_sec,
                    ROUND(AVG_TIMER_WAIT/1000000000, 4) as avg_time_sec,
                    SUM_ROWS_EXAMINED as rows_examined,
                    SUM_ROWS_SENT as rows_sent,
                    SUM_ROWS_AFFECTED as rows_affected,
                    FIRST_SEEN as first_seen,
                    LAST_SEEN as last_seen
                FROM performance_schema.events_statements_summary_by_digest
                WHERE DIGEST_TEXT IS NOT NULL
                    AND DIGEST_TEXT NOT LIKE '%%performance_schema%%'
                ORDER BY COUNT_STAR DESC
                LIMIT 20
                """;
            List<Map<String, Object>> hotspotSQLs = jdbcTemplate.queryForList(sql);

            //存储到Redis有序集合(按执行次数排序)
            for (Map<String, Object> sqlStat : hotspotSQLs) {
                String sqlDigest = (String) sqlStat.get("sql_digest");
                long executionCount = ((Number)sqlStat.get("execution_count")).longValue();

                //使用有序集合存储，分数为执行次数
                redisTemplate.opsForZSet().add(
                        HOTSPOT_SQL_KEY,
                        sqlDigest,
                        (double) executionCount
                );

                //同时存储SQL详细统计信息
                String detailKey = HOTSPOT_SQL_KEY+":detail:"+
                        DigestUtils.md5DigestAsHex(sqlDigest.getBytes());
                redisTemplate.opsForHash().putAll(detailKey, sqlStat);
                redisTemplate.expire(detailKey,1, TimeUnit.HOURS);

            }
            redisTemplate.opsForZSet().removeRange(HOTSPOT_SQL_KEY,0,-101);
            log.info("收集MySQL热点SQL成功,共收集{}条",hotspotSQLs.size());

        }catch (Exception e){
            log.error("收集MySQL热点SQL失败", e);
        }
    }

    // 1.3 收集慢查询
    @Scheduled(fixedDelay = 60000) // 每分钟执行
    public void collectSlowQueries() {
        try {
            // 检查慢查询日志表（如果开启）
            String slowQuerySql = """
                SELECT 
                    sql_text,
                    query_time,
                    lock_time,
                    rows_sent,
                    rows_examined,
                    db,
                    user_host,
                    start_time
                FROM mysql.slow_log
                WHERE start_time >= DATE_SUB(NOW(), INTERVAL 5 MINUTE)
                ORDER BY query_time DESC
                LIMIT 50
                """;

            List<Map<String, Object>> slowQueries = jdbcTemplate.queryForList(slowQuerySql);

            // 存储到Redis列表
            for (Map<String, Object> slowQuery : slowQueries) {
                String queryKey = SLOW_QUERY_KEY + ":" +
                        ((java.sql.Timestamp) slowQuery.get("start_time")).getTime();
                redisTemplate.opsForValue().set(queryKey, slowQuery, 24, TimeUnit.HOURS);
            }

            // 更新慢查询计数
            Long slowQueryCount = jdbcTemplate.queryForObject(
                    "SHOW GLOBAL STATUS LIKE 'Slow_queries'",
                    (rs, rowNum) -> rs.getLong("Value")
            );

            redisTemplate.opsForValue().set(
                    "mysql:slow:count",
                    slowQueryCount,
                    5, TimeUnit.MINUTES
            );

        } catch (Exception e) {
            log.error("收集慢查询失败", e);
        }
    }
}
