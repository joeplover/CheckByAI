-- 使用数据库
USE check_ai;

-- 更新所有用户的密码为123456（使用BCrypt加密）
UPDATE `user` SET password = '$2a$10$EixZaYVK1fsbw1ZfbX3OXePaWxn96p36WQoeG6Lruj3vjPGga31lW' WHERE id = '1';
UPDATE `user` SET password = '$2a$10$EixZaYVK1fsbw1ZfbX3OXePaWxn96p36WQoeG6Lruj3vjPGga31lW' WHERE id = '2';
UPDATE `user` SET password = '$2a$10$EixZaYVK1fsbw1ZfbX3OXePaWxn96p36WQoeG6Lruj3vjPGga31lW' WHERE id = '3';

-- 插入测试用户（如果不存在）
INSERT IGNORE INTO `user` (id, username, password, nickname, email, phone, status, create_time, update_time)
VALUES
('1', 'admin', '$2a$10$EixZaYVK1fsbw1ZfbX3OXePaWxn96p36WQoeG6Lruj3vjPGga31lW', '管理员', 'admin@example.com', '13800138000', 1, NOW(), NOW()),
('2', 'test', '$2a$10$EixZaYVK1fsbw1ZfbX3OXePaWxn96p36WQoeG6Lruj3vjPGga31lW', '测试用户', 'test@example.com', '13800138001', 1, NOW(), NOW()),
('3', 'user1', '$2a$10$EixZaYVK1fsbw1ZfbX3OXePaWxn96p36WQoeG6Lruj3vjPGga31lW', '普通用户', 'user1@example.com', '13800138002', 1, NOW(), NOW());
