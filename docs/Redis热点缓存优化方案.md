## 现状与热点识别

从代码与接口调用看，热点主要集中在：

- **任务列表**：`GET /api/tasks`（按用户维度频繁刷新）
- **任务结果**：`GET /api/task/{taskId}/results`、`GET /api/task/original/{originalTaskId}/results`
- **MySQL监控/热点SQL统计**：已有写入Redis的迹象（例如 `mysql:hotspot:sql` 等key）

## 目标

- 降低 DB 读取压力、降低 P99 延迟
- 防止 **缓存击穿/雪崩/穿透**
- 提升热点key的可观测性与可控性

## Key 设计建议

- **统一前缀**：`checkai:{module}:{biz}:{id}`
- **避免大key**：结果数据尽量分页/分片；必要时只缓存摘要
- **避免KEYS**：生产环境禁用 `KEYS pattern`，需要遍历用 `SCAN`

## TTL 与抖动（防雪崩）

对同一批key不要设完全相同TTL：

- 基础TTL：例如 30s / 60s
- 抖动：随机加 0~10s

（Spring Cache默认不支持“每次写入随机TTL”，可在业务层用 RedisTemplate 手动设置，或自定义 CacheWriter。）

## 缓存击穿（热点key过期瞬间）

建议方案（择一落地）：

- **互斥锁**：`SET lock:key NX PX 3000`，只有一个线程回源
- **单飞（SingleFlight）**：同一key并发请求合并（可用本地ConcurrentHashMap + Future）
- **逻辑过期**：value里带 `expireAt`，后台异步刷新，前台先返回旧值

## 缓存穿透（不存在的数据反复查）

- **缓存空值**：短TTL（例如 5~30s）
- **布隆过滤器**：适合“ID集合稳定、查询量大”的场景（如订单号/任务ID校验）

## 多级缓存（热点更热时）

推荐 **L1 本地缓存 + L2 Redis**：

- L1：Caffeine（毫秒级，适合超热点）
- L2：Redis（跨实例共享）

注意：

- 需要明确一致性边界（写操作触发L1/L2失效）
- 避免本地缓存无限增长（size上限 + TTL）

## 与本项目的落地建议（已做/可继续做）

已做（代码已落地）：

- **任务列表/任务结果** 使用 Spring Cache + Redis，回调写入后主动失效

建议继续做：

- **缓存命名与TTL分级**
  - `tasksByUser`：10~30s
  - `taskResultsByUserAndTask`：5~15s（更频繁变化）
  - `originalTaskResultsByUserAndOriginal`：同上
- **结果缓存“摘要化”**
  - 首页只要最近5条任务，可缓存“最近N条任务ID+摘要”，详情再按taskId拉取
- **监控**
  - 统计命中率、回源次数、锁等待次数


