## 目标

把“上传Excel → 触发AI/工作流处理 → 回调入库/更新任务状态”从同步链路改为**异步消息驱动**，实现：

- **削峰填谷**：突发上传不把后端/外部AI接口打挂
- **可靠性**：失败可重试、可死信、可追踪
- **可扩展**：可水平扩容消费者

## 推荐技术栈（Spring Boot）

- 依赖：`spring-boot-starter-amqp`
- Broker：RabbitMQ（建议带 Management 插件）

## 典型消息拓扑（建议）

- **Exchange**：`checkai.task.exchange`（type：`topic`）
- **Queue**：
  - `checkai.task.process.q`（处理任务）
  - `checkai.task.process.dlq`（死信队列）
- **RoutingKey**：
  - `task.process`（主消费）
  - `task.process.retry`（可选：延迟/重试）

### 死信（DLX）建议

主队列配置：

- `x-dead-letter-exchange = checkai.task.exchange`
- `x-dead-letter-routing-key = task.process.dlq`

## 消息模型（建议JSON）

建议最小字段：

- **messageId**：全局唯一（用于幂等）
- **taskId**：业务任务ID（对应表 `task.task_id`）
- **userId**
- **payloadRef**：大数据不要直接塞消息里，建议“对象存储/数据库引用”
- **createdAt**
- **traceId**（便于链路追踪）

## 生产者（Producer）关键点

- **发布确认**（publisher confirm）：确保消息进broker
- **可重试**：网络抖动/瞬时不可用时，重试要有上限+退避
- **消息大小控制**：Excel解析后的大JSON不要直接入消息，避免单条消息过大导致内存/网络压力

## 消费者（Consumer）关键点

- **手动ACK**：处理成功再ACK；失败则NACK/requeue或进入DLQ
- **并发**：根据外部AI接口QPS与DB承载设置并发（例如 `concurrency=4~16`）
- **超时**：调用外部AI/工作流必须有超时与取消策略

## 幂等设计（强烈建议）

至少保证“同一条消息重复投递不会重复写库/重复回调”：

- **幂等Key**：`idempotent:task:{taskId}:msg:{messageId}`
- **Redis SETNX + TTL**：
  - 成功写入后再写“完成态”或直接把key保留一段时间
  - TTL建议与最大重试窗口对齐（例如 24h）

## 重试策略（建议）

不要无限重试：

- **业务可重试错误**：外部AI 5xx/超时 → 退避重试（指数退避 + 抖动）
- **不可重试错误**：参数不合法/格式错误 → 直接DLQ

实现方式：

- **方式A（简单）**：消费失败 → NACK 丢到DLQ，人工/定时任务处理
- **方式B（推荐）**：使用延迟队列或 TTL+DLX 实现“自动延迟重试”

## 与本项目结合的落地步骤（建议路径）

- **Step 1**：上传接口只做：
  - 校验文件
  - 生成 `task.task_id`
  - 入库任务状态 `PENDING`
  - 发送消息到 `task.process`
- **Step 2**：新增消费者服务（可在同应用中先落地）：
  - 拉取任务数据（或从对象存储取文件）
  - 调用 Coze / LangChain Agent
  - 写入 `callback_data`
  - 更新 `task.status/progress`
- **Step 3**：监控与运维：
  - RabbitMQ Management 观察队列堆积、消费速率
  - 记录 messageId/taskId/traceId 日志

## 最小配置示例（YAML片段）

（仅示意，具体以你们部署环境为准）

- `spring.rabbitmq.host/port/username/password`
- `spring.rabbitmq.publisher-confirm-type=correlated`
- `spring.rabbitmq.publisher-returns=true`


