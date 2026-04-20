package com.checkai.interceptor;

import com.checkai.entity.User;
import com.checkai.mapper.UserMapper;
import com.checkai.util.CurrentUserHolder;
import com.checkai.util.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * JWT拦截器，用于验证JWT token和管理当前用户信息
 */
@Component
public class JwtInterceptor implements HandlerInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(JwtInterceptor.class);

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserMapper userMapper;

    /**
     * 在请求处理之前执行
     * @param request 请求对象
     * @param response 响应对象
     * @param handler 处理器
     * @return 是否继续执行
     * @throws Exception 异常
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 处理OPTIONS请求，直接放行
        if (request.getMethod().equals("OPTIONS")) {
            logger.debug("OPTIONS请求，跳过JWT验证");
            return true;
        }
        
        // 获取请求URL
        String requestURI = request.getRequestURI();
        logger.debug("请求URL: {}", requestURI);
        
        // 白名单：登录、注册、回调和错误接口不需要JWT验证
        if (requestURI.equals("/auth/login") || requestURI.equals("/auth/register") || 
            requestURI.equals("/api/callback") || requestURI.equals("/error")) {
            logger.debug("白名单URL，跳过JWT验证");
            return true;
        }
        
        // 获取请求头中的Authorization字段
        String authorizationHeader = request.getHeader("Authorization");

        // 检查Authorization头是否存在且格式正确
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            logger.warn("缺少或无效的Authorization头");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"success\":false,\"message\":\"未授权访问\"}");
            return false;
        }

        // 提取JWT token
        String token = authorizationHeader.substring(7);

        try {
            // 从token中提取用户ID和用户名
            String userId = jwtUtil.getUserIdFromToken(token);
            String username = jwtUtil.getUsernameFromToken(token);
            logger.debug("从token中提取的用户信息 - ID: {}, 用户名: {}", userId, username);

            // 验证token
            boolean isValid = jwtUtil.validateToken(token, userId, username);
            if (!isValid) {
                logger.warn("无效的JWT token");
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.getWriter().write("{\"success\":false,\"message\":\"无效的token\"}");
                return false;
            }

            // 查询用户信息
            User user = userMapper.selectById(userId);
            if (user == null) {
                logger.warn("用户不存在: {}", userId);
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.getWriter().write("{\"success\":false,\"message\":\"用户不存在\"}");
                return false;
            }

            // 检查用户状态
            if (user.getStatus() != 1) {
                logger.warn("用户已被禁用: {}", username);
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.getWriter().write("{\"success\":false,\"message\":\"用户已被禁用\"}");
                return false;
            }

            // 将用户信息存储到ThreadLocal中
            CurrentUserHolder.set(user);
            logger.debug("用户信息已存储到ThreadLocal: {}", username);

            return true;
        } catch (Exception e) {
            logger.error("JWT token验证失败: {}", e.getMessage());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"success\":false,\"message\":\"token验证失败\"}");
            return false;
        }
    }

    /**
     * 在请求处理之后执行
     * @param request 请求对象
     * @param response 响应对象
     * @param handler 处理器
     * @param ex 异常
     * @throws Exception 异常
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        // 清除ThreadLocal中的用户信息，防止内存泄漏
        CurrentUserHolder.clear();
        logger.debug("ThreadLocal中的用户信息已清除");
    }
}
