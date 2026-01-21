package com.checkai.config;

import com.checkai.interceptor.JwtInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web配置类，用于配置拦截器和静态资源等
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Autowired
    private JwtInterceptor jwtInterceptor;

    /**
     * 注册拦截器
     * @param registry 拦截器注册表
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 注册JWT拦截器
        registry.addInterceptor(jwtInterceptor)
                // 拦截所有路径
                .addPathPatterns("/**")
                // 设置匿名访问路径（不需要token验证）
                .excludePathPatterns(
                        "/auth/login",    // 登录接口
                        "/auth/register", // 注册接口
                        "/api/callback",  // 回调接口
                        "/swagger-ui/**",  // Swagger UI
                        "/v3/api-docs/**", // Swagger API文档
                        "/error"           // 错误处理路径
                );
    }
}