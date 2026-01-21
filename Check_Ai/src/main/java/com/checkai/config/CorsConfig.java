package com.checkai.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 全局CORS配置
 * 允许来自前端的跨域请求
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                // 允许前端地址
//                .allowedOrigins("http://localhost:5173", "http://localhost:5174", "http://localhost:80")
                .allowedOriginPatterns("*")
                // 允许请求方法
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                // 允许请求头
                .allowedHeaders("*")
                // 允许携带凭证（如Cookie）
                .allowCredentials(true)
                // 预检请求缓存时间
                .maxAge(3600);
    }
}
