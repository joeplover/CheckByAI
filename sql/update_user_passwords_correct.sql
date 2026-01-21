-- 使用数据库
USE check_ai;

-- 更新所有用户的密码为123456（使用正确的BCrypt加密）
-- 此哈希值是通过Spring Security BCryptPasswordEncoder生成的，对应密码：123456
UPDATE `user` SET password = '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy' WHERE id IN ('1', '2', '3');

-- 验证更新结果
SELECT username, password FROM `user` WHERE id IN ('1', '2', '3');
