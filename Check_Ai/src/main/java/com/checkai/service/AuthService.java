package com.checkai.service;

import com.checkai.dto.LoginRequest;
import com.checkai.dto.LoginResponse;
import com.checkai.dto.RegisterRequest;
import com.checkai.entity.User;
import com.checkai.mapper.UserMapper;
import com.checkai.util.JwtUtil;
import com.checkai.util.MD5Util;
import com.checkai.util.ShortIdUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Date;

@Service
public class AuthService {

    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);

    @Autowired
    private UserMapper userMapper;
    
    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private PasswordEncoder passwordEncoder;

    /**
     * 用户登录
     */
    public LoginResponse login(LoginRequest loginRequest) {
        logger.info("收到登录请求，用户名：{}", loginRequest.getUsername());
        LoginResponse response = new LoginResponse();

        // 根据用户名查询用户
        User user = userMapper.selectByUsername(loginRequest.getUsername());
        logger.info("查询用户结果：{}", user != null ? user.getUsername() : "不存在");
        
        if (user == null) {
            response.setSuccess(false);
            response.setMessage("用户名不存在");
            logger.info("登录失败：用户名不存在，用户名：{}", loginRequest.getUsername());
            return response;
        }

        // 检查用户状态
        if (user.getStatus() != 1) {
            response.setSuccess(false);
            response.setMessage("用户已被禁用");
            logger.info("登录失败：用户已被禁用，用户名：{}", loginRequest.getUsername());
            return response;
        }

        // 验证密码：优先支持BCrypt；兼容老的MD5（登录成功后自动升级为BCrypt）
        boolean passwordMatch = passwordMatches(loginRequest.getPassword(), user.getPassword());
        
        if (!passwordMatch) {
            response.setSuccess(false);
            response.setMessage("密码错误");
            logger.info("登录失败：密码错误，用户名：{}", loginRequest.getUsername());
            return response;
        }

        // 若是老MD5密码，登录成功后自动升级为BCrypt，逐步消除MD5存量
        upgradePasswordIfLegacyMd5(loginRequest.getPassword(), user);

        // 生成JWT令牌
        String token = generateJwtToken(user);
        logger.info("生成JWT令牌成功，用户名：{}", loginRequest.getUsername());

        response.setSuccess(true);
        response.setToken(token);
        response.setUserId(user.getId());
        response.setUsername(user.getUsername());
        response.setNickname(user.getNickname());
        response.setMessage("登录成功");

        logger.info("登录成功，用户名：{}", loginRequest.getUsername());
        return response;
    }

    /**
     * 生成JWT令牌
     */
    private String generateJwtToken(User user) {
        return jwtUtil.generateToken(user.getId(), user.getUsername());
    }
    
    /**
     * 用户注册
     */
    public LoginResponse register(RegisterRequest registerRequest) {
        logger.info("收到注册请求，用户名：{}", registerRequest.getUsername());
        LoginResponse response = new LoginResponse();
        
        // 检查用户名是否已存在
        User existingUser = userMapper.selectByUsername(registerRequest.getUsername());
        if (existingUser != null) {
            response.setSuccess(false);
            response.setMessage("用户名已存在");
            logger.info("注册失败：用户名已存在，用户名：{}", registerRequest.getUsername());
            return response;
        }
        
        // 创建新用户
        User newUser = new User();
        newUser.setId(ShortIdUtil.generateShortId()); // 生成8位短ID
        newUser.setUsername(registerRequest.getUsername());
        // 使用BCrypt存储密码（更安全）
        newUser.setPassword(passwordEncoder.encode(registerRequest.getPassword()));
        newUser.setNickname(registerRequest.getNickname());
        newUser.setEmail(registerRequest.getEmail());
        newUser.setPhone(registerRequest.getPhone());
        newUser.setStatus(1); // 默认启用
        newUser.setCreateTime(new Date());
        newUser.setUpdateTime(new Date());
        
        // 保存到数据库
        int result = userMapper.insert(newUser);
        if (result > 0) {
            response.setSuccess(true);
            response.setMessage("注册成功");
            logger.info("注册成功，用户名：{}", registerRequest.getUsername());
        } else {
            response.setSuccess(false);
            response.setMessage("注册失败，请重试");
            logger.info("注册失败：数据库插入失败，用户名：{}", registerRequest.getUsername());
        }
        
        return response;
    }

    private boolean passwordMatches(String rawPassword, String storedPassword) {
        if (rawPassword == null || storedPassword == null) {
            return false;
        }

        // 识别BCrypt格式（$2a$/$2b$/$2y$ 开头）
        if (storedPassword.startsWith("$2a$") || storedPassword.startsWith("$2b$") || storedPassword.startsWith("$2y$")) {
            return passwordEncoder.matches(rawPassword, storedPassword);
        }

        // 兼容旧MD5（32位hex）
        if (isLikelyMd5Hex(storedPassword)) {
            return MD5Util.matches(rawPassword, storedPassword);
        }

        // 兜底：先尝试BCrypt，再尝试MD5
        try {
            if (passwordEncoder.matches(rawPassword, storedPassword)) {
                return true;
            }
        } catch (Exception ignored) {
        }
        return MD5Util.matches(rawPassword, storedPassword);
    }

    private void upgradePasswordIfLegacyMd5(String rawPassword, User user) {
        if (user == null || user.getPassword() == null) {
            return;
        }
        if (!isLikelyMd5Hex(user.getPassword())) {
            return;
        }

        try {
            String newHash = passwordEncoder.encode(rawPassword);
            User update = new User();
            update.setId(user.getId());
            update.setPassword(newHash);
            update.setUpdateTime(new Date());
            userMapper.updateById(update);
            logger.info("用户密码已从MD5升级为BCrypt，userId={}", user.getId());
        } catch (Exception e) {
            // 升级失败不影响本次登录
            logger.warn("用户密码从MD5升级为BCrypt失败，userId={}, err={}", user.getId(), e.getMessage());
        }
    }

    private boolean isLikelyMd5Hex(String s) {
        if (s.length() != 32) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean isHex = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
            if (!isHex) {
                return false;
            }
        }
        return true;
    }
}
