CREATE TABLE IF NOT EXISTS `task_review` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `task_id` VARCHAR(64) NOT NULL COMMENT '任务ID',
  `user_id` VARCHAR(64) NOT NULL COMMENT '用户ID',
  `review_status` VARCHAR(32) NOT NULL DEFAULT 'UNREVIEWED' COMMENT '复核状态',
  `risk_level` VARCHAR(32) DEFAULT NULL COMMENT '风险等级',
  `tags` TEXT COMMENT '标签',
  `remark` TEXT COMMENT '复核备注',
  `review_result` TEXT COMMENT '复核结论',
  `reviewer` VARCHAR(64) DEFAULT NULL COMMENT '复核人',
  `review_time` DATETIME DEFAULT NULL COMMENT '复核时间',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_task_review_user_task` (`user_id`, `task_id`),
  KEY `idx_task_review_task_id` (`task_id`),
  KEY `idx_task_review_user_status` (`user_id`, `review_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='任务复核表';
