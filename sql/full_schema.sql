-- 创建数据库（如果不存在）
CREATE DATABASE IF NOT EXISTS check_ai DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 使用数据库
USE check_ai;

-- 1. 创建用户表
CREATE TABLE IF NOT EXISTS `user` (
    id VARCHAR(36) NOT NULL PRIMARY KEY COMMENT '主键UUID',
    username VARCHAR(50) NOT NULL UNIQUE COMMENT '用户名',
    password VARCHAR(100) NOT NULL COMMENT '密码（加密存储）',
    nickname VARCHAR(50) COMMENT '昵称',
    email VARCHAR(100) UNIQUE COMMENT '邮箱',
    phone VARCHAR(20) UNIQUE COMMENT '手机号',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0-禁用, 1-启用',
    create_time DATETIME NOT NULL COMMENT '创建时间',
    update_time DATETIME NOT NULL COMMENT '更新时间',
    last_login_time DATETIME COMMENT '最后登录时间',
    INDEX idx_username (username),
    INDEX idx_email (email),
    INDEX idx_phone (phone),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- 2. 创建任务表
CREATE TABLE IF NOT EXISTS task (
    id VARCHAR(36) NOT NULL PRIMARY KEY COMMENT '主键UUID',
    task_id VARCHAR(100) NOT NULL COMMENT '任务ID',
    original_task_id VARCHAR(36) COMMENT '原始任务ID（用于批次任务）',
    user_id VARCHAR(36) NOT NULL COMMENT '关联的用户ID',
    batch_number INT COMMENT '批次号',
    total_batches INT COMMENT '总批次',
    status VARCHAR(20) NOT NULL COMMENT '状态：PENDING-待处理, SENT-已发送, COMPLETED-已完成, FAILED-失败',
    data_content TEXT COMMENT '数据内容（JSON格式）',
    create_time DATETIME NOT NULL COMMENT '创建时间',
    update_time DATETIME NOT NULL COMMENT '更新时间',
    timeout_time DATETIME COMMENT '超时时间',
    progress INT COMMENT '当前进度',
    total_progress INT COMMENT '总进度',
    INDEX idx_task_id (task_id),
    INDEX idx_original_task_id (original_task_id),
    INDEX idx_user_id (user_id),
    INDEX idx_status (status),
    INDEX idx_create_time (create_time),
    CONSTRAINT fk_task_user FOREIGN KEY (user_id) REFERENCES `user`(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='任务表';

-- 3. 创建回调数据表
CREATE TABLE IF NOT EXISTS callback_data (
    id VARCHAR(36) NOT NULL PRIMARY KEY COMMENT '主键UUID',
    task_id VARCHAR(100) NOT NULL COMMENT '任务ID',
    original_task_id VARCHAR(36) COMMENT '原始任务ID',
    user_id VARCHAR(36) NOT NULL COMMENT '关联的用户ID',
    data TEXT NOT NULL COMMENT '回调数据',
    receive_time DATETIME NOT NULL COMMENT '接收时间',
    INDEX idx_task_id (task_id),
    INDEX idx_original_task_id (original_task_id),
    INDEX idx_user_id (user_id),
    INDEX idx_receive_time (receive_time),
    CONSTRAINT fk_callback_user FOREIGN KEY (user_id) REFERENCES `user`(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='回调数据表';

-- 插入测试用户数据
-- 密码：123456，使用BCrypt加密
INSERT INTO `user` (id, username, password, nickname, email, phone, status, create_time, update_time)
VALUES
('1', 'admin', '$2a$10$EixZaYVK1fsbw1ZfbX3OXePaWxn96p36WQoeG6Lruj3vjPGga31lW', '管理员', 'admin@example.com', '13800138000', 1, NOW(), NOW()),
('2', 'test', '$2a$10$EixZaYVK1fsbw1ZfbX3OXePaWxn96p36WQoeG6Lruj3vjPGga31lW', '测试用户', 'test@example.com', '13800138001', 1, NOW(), NOW()),
('3', 'user1', '$2a$10$EixZaYVK1fsbw1ZfbX3OXePaWxn96p36WQoeG6Lruj3vjPGga31lW', '普通用户', 'user1@example.com', '13800138002', 1, NOW(), NOW());

-- 插入测试任务数据
INSERT INTO task (id, task_id, original_task_id, user_id, batch_number, total_batches, status, data_content, create_time, update_time, timeout_time, progress, total_progress)
VALUES
-- 非批次任务示例
('1001', 'task-20260120-0001', NULL, '1', NULL, NULL, 'COMPLETED', '[{"name": "测试数据1", "value": "123"}, {"name": "测试数据2", "value": "456"}]', NOW() - INTERVAL 2 HOUR, NOW() - INTERVAL 1 HOUR, NOW() - INTERVAL 1 HOUR + INTERVAL 8 MINUTE, 2, 2),
('1002', 'task-20260120-0002', NULL, '2', NULL, NULL, 'SENT', '[{"name": "测试数据3", "value": "789"}]', NOW() - INTERVAL 30 MINUTE, NOW() - INTERVAL 30 MINUTE, NOW() - INTERVAL 30 MINUTE + INTERVAL 8 MINUTE, 1, 1),
('1003', 'task-20260120-0003', NULL, '3', NULL, NULL, 'PENDING', '[{"name": "测试数据4", "value": "321"}, {"name": "测试数据5", "value": "654"}]', NOW(), NOW(), NOW() + INTERVAL 8 MINUTE, 0, 2),

-- 批次任务示例
('2001', 'batch-task-20260120-0001-1', '2000', '1', 1, 3, 'COMPLETED', '[{"name": "批次数据1", "value": "100"}]', NOW() - INTERVAL 3 HOUR, NOW() - INTERVAL 2 HOUR, NOW() - INTERVAL 3 HOUR + INTERVAL 8 MINUTE, 1, 1),
('2002', 'batch-task-20260120-0001-2', '2000', '1', 2, 3, 'COMPLETED', '[{"name": "批次数据2", "value": "200"}]', NOW() - INTERVAL 3 HOUR, NOW() - INTERVAL 2 HOUR, NOW() - INTERVAL 3 HOUR + INTERVAL 8 MINUTE, 1, 1),
('2003', 'batch-task-20260120-0001-3', '2000', '1', 3, 3, 'COMPLETED', '[{"name": "批次数据3", "value": "300"}]', NOW() - INTERVAL 3 HOUR, NOW() - INTERVAL 2 HOUR, NOW() - INTERVAL 3 HOUR + INTERVAL 8 MINUTE, 1, 1),
('2000', 'batch-task-20260120-0001', NULL, '1', NULL, 3, 'COMPLETED', '[{"name": "批次数据1", "value": "100"}, {"name": "批次数据2", "value": "200"}, {"name": "批次数据3", "value": "300"}]', NOW() - INTERVAL 3 HOUR, NOW() - INTERVAL 2 HOUR, NOW() - INTERVAL 3 HOUR + INTERVAL 8 MINUTE, 3, 3),

-- 失败任务示例
('3001', 'task-20260120-0004', NULL, '2', NULL, NULL, 'FAILED', '[{"name": "失败数据", "value": "error"}]', NOW() - INTERVAL 4 HOUR, NOW() - INTERVAL 4 HOUR + INTERVAL 8 MINUTE, NOW() - INTERVAL 4 HOUR + INTERVAL 8 MINUTE, 0, 1);

-- 插入测试回调数据
INSERT INTO callback_data (id, task_id, original_task_id, user_id, data, receive_time)
VALUES
-- 非批次任务回调
('4001', 'task-20260120-0001', NULL, '1', '{"status": "success", "result": "处理完成", "data": [{"name": "测试数据1", "value": "123", "processed": true}, {"name": "测试数据2", "value": "456", "processed": true}]}', NOW() - INTERVAL 1 HOUR),

-- 批次任务回调
('4002', 'batch-task-20260120-0001-1', '2000', '1', '{"status": "success", "result": "批次1处理完成", "data": [{"name": "批次数据1", "value": "100", "processed": true}]}', NOW() - INTERVAL 2 HOUR),
('4003', 'batch-task-20260120-0001-2', '2000', '1', '{"status": "success", "result": "批次2处理完成", "data": [{"name": "批次数据2", "value": "200", "processed": true}]}', NOW() - INTERVAL 2 HOUR + INTERVAL 10 MINUTE),
('4004', 'batch-task-20260120-0001-3', '2000', '1', '{"status": "success", "result": "批次3处理完成", "data": [{"name": "批次数据3", "value": "300", "processed": true}]}', NOW() - INTERVAL 2 HOUR + INTERVAL 20 MINUTE);

