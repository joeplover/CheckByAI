package com.checkai.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

@Configuration
public class DatabaseConfig {

    @Bean
    @Primary
    @ConditionalOnProperty(name = "spring.datasource.url", matchIfMissing = true)
    public DataSource dataSource() {
        HikariConfig config = new HikariConfig();
        
        // 从环境变量或配置文件读取数据库配置
        config.setJdbcUrl(System.getenv("DB_URL") != null ? System.getenv("DB_URL") : 
                         System.getProperty("spring.datasource.url", "jdbc:mysql://192.168.91.132:3306/logisticsAi?useUnicode=true&characterEncoding=utf-8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true"));
        config.setUsername(System.getenv("DB_USERNAME") != null ? System.getenv("DB_USERNAME") : 
                         System.getProperty("spring.datasource.username", "root"));
        config.setPassword(System.getenv("DB_PASSWORD") != null ? System.getenv("DB_PASSWORD") : 
                         System.getProperty("spring.datasource.password", "root123"));
        config.setDriverClassName(System.getProperty("spring.datasource.driver-class-name", "com.mysql.cj.jdbc.Driver"));
        
        // HikariCP 配置
        config.setPoolName("HikariPool-RedisCacheDemo");
        config.setMinimumIdle(5);
        config.setMaximumPoolSize(20);
        config.setAutoCommit(true);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);
        config.setConnectionTestQuery("SELECT 1");
        config.setTransactionIsolation("TRANSACTION_REPEATABLE_READ");
        config.setValidationTimeout(5000);
        config.setLeakDetectionThreshold(60000);
        
        // 关键：添加连接验证和异常处理
        config.setConnectionInitSql("SELECT 1");
        config.setInitializationFailTimeout(-1); // 设置为-1表示即使初始化失败也不影响应用启动
        
        return new HikariDataSource(config);
    }
}