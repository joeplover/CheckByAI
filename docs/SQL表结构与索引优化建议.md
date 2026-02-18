## 现状（基于 `sql/full_schema.sql` 与代码查询模式）

后端主要查询模式：

- 任务列表：`task where user_id=? order by create_time desc`
- 任务结果：`callback_data where task_id=? order by receive_time desc`
- 原始任务结果：`callback_data where original_task_id=? order by receive_time desc`
- 批次任务列表：`task where original_task_id=? order by batch_number asc`

目前 `full_schema.sql` 里多为**单列索引**，在 `where + order by` 场景下建议补充**复合索引**，减少 filesort 与回表开销。

## 索引优化建议（推荐）

### `task` 表

- **按用户查任务并按时间排序**：
  - `INDEX idx_task_user_create_time (user_id, create_time)`
- **按原始任务查批次并按批次号排序**：
  - `INDEX idx_task_original_batch (original_task_id, batch_number)`
- **task_id 唯一性**（如果业务保证唯一）：
  - `UNIQUE KEY uk_task_task_id (task_id)`

### `callback_data` 表

- **按 task_id 查结果并按时间排序**：
  - `INDEX idx_cb_task_receive_time (task_id, receive_time)`
- **按 original_task_id 查结果并按时间排序**：
  - `INDEX idx_cb_original_receive_time (original_task_id, receive_time)`

### `user` 表

- `username/email/phone` 已是 UNIQUE，**无需再重复建普通索引**（避免冗余）

## 字段与表设计建议（择机重构）

- **表名 `user`**：在部分数据库/工具链里是保留字，建议未来迁移到 `users`（需要全链路改造，谨慎）。
- **时间字段默认值**：
  - `create_time` 建议默认 `CURRENT_TIMESTAMP`
  - `update_time` 建议 `ON UPDATE CURRENT_TIMESTAMP`
- **大字段**：
  - `task.data_content` 若长期存JSON，建议改 `JSON` 类型（MySQL 5.7+），并为常用字段建生成列+索引。
- **回调数据 Base64**：
  - Base64会放大体积（约 +33%），如果没有强需求，建议直接存原文（或存压缩后的内容+标记）。

## 可直接执行的DDL脚本

见：`sql/optimization_indexes.sql`


