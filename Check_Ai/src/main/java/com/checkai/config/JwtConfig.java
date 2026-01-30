package com.checkai.config;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;


/**
 * JWT配置类
 * 用于管理JWT的密钥和过期时间配置
 */
@Configuration
public class JwtConfig {

    /**
     * JWT密钥，从配置文件中读取
     * 用于签名和验证JWT令牌
     */
    @Value("${jwt.secret}")
    private String secretKey;

    /**
     * JWT过期时间（单位：毫秒）
     * 从配置文件中读取，用于设置令牌的有效期
     */
    @Value("${jwt.expiration}")
    private long expiration;

    /**
     * 创建JWT密钥Bean
     * 将字符串格式的密钥转换为SecretKey对象
     * 使用HMAC-SHA算法进行签名
     * 
     * @return SecretKey对象，用于JWT的签名和验证
     */
    @Bean
    public SecretKey secretKey() {
        return Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 获取JWT过期时间
     * 
     * @return JWT令牌的过期时间（毫秒）
     */
    public long getExpiration() {
        return expiration;
    }
}
