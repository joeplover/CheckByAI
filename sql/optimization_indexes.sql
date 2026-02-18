-- 索引优化建议（在低峰期执行）
-- 注意：执行前请确认表数据量与业务唯一性约束，避免锁表影响。

USE check_ai;

-- task：按用户查任务列表 + 按时间排序
ALTER TABLE task
  ADD INDEX idx_task_user_create_time (user_id, create_time);

-- task：按原始任务查批次 + 按批次号排序
ALTER TABLE task
  ADD INDEX idx_task_original_batch (original_task_id, batch_number);

-- task：如果业务保证task_id全局唯一，建议加唯一索引
-- 若存在历史重复数据，请先清理后再执行
-- ALTER TABLE task
--   ADD UNIQUE KEY uk_task_task_id (task_id);

-- callback_data：按task_id查结果 + 按接收时间排序
ALTER TABLE callback_data
  ADD INDEX idx_cb_task_receive_time (task_id, receive_time);

-- callback_data：按original_task_id查结果 + 按接收时间排序
ALTER TABLE callback_data
  ADD INDEX idx_cb_original_receive_time (original_task_id, receive_time);


