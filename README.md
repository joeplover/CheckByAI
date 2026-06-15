# AI Check System

AI Check System 是一个基于人工智能的物流单据智能审核系统，集成 **Coze 工作流** 与 **LangGraph 多智能体** 双 AI 引擎，实现 Excel 物流数据解析、图片 OCR 识别、重量一致性校验及人工复核的全流程自动化。

## 系统架构

```
┌─────────────────────────────────────────────────────────────┐
│                     用户层 (Vue 3)                          │
│          文件上传 / 任务管理 / 审核工作台 / AI 助手           │
└──────────────────────┬──────────────────────────────────────┘
                       │ HTTP / Axios
┌──────────────────────▼──────────────────────────────────────┐
│                     API 层 (Spring Boot)                     │
│         Auth / Task / Callback / Review / AI Chat           │
└──┬───────────────┬──────────────┬─────────────────┬─────────┘
   │               │              │                 │
   ▼               ▼              ▼                 ▼
┌─────────┐ ┌───────────┐ ┌──────────┐ ┌──────────────────┐
│ RabbitMQ│ │  Coze AI  │ │LangGraph │ │  RAG 知识库       │
│ 异步队列 │ │  工作流   │ │多Agent   │ │(Milvus + Ollama)  │
│ 任务分发 │ │  审核API  │ │OCR+校验  │ │ 文档向量检索       │
└─────────┘ └───────────┘ └──────────┘ └──────────────────┘
```

### AI 引擎双通道

1. **Coze 工作流**（主流程）：通过 Coze API 调用预定义工作流（workflow-id: 7595443144305852425），处理大批量 Excel 数据审核
2. **LangGraph 多智能体**（本地 Python 服务）：基于 LangGraph 构建的多 Agent 协作系统，执行 Excel 解析 → 图片下载 + OCR → LLM 重量一致性校验

### 消息驱动架构

采用 **RabbitMQ** 异步消息队列处理任务分发，支持：
- 任务批量提交与排队处理
- 死信队列（DLQ）异常隔离
- 幂等性消费保证
- 任务超时自动处理

## 核心组件

### 后端 (Spring Boot 3.2.2)

| 模块 | 说明 |
|------|------|
| `WorkflowService` | Coze 工作流集成，批量提交 Excel 数据，接收回调结果 |
| `AiAssistantService` | 基于 LangChain4j + DeepSeek 的 AI 对话助手 |
| `RagService` | RAG 检索增强生成，支持文档向量化存储与语义检索 |
| `TaskReviewService` | 人工审核工作台，支持复核状态流转与风险等级标注 |
| `CallbackService` | Coze 工作流回调处理，支持分批回调数据聚合 |
| `TaskProcessConsumer` | RabbitMQ 异步消费者，解耦任务提交与处理 |
| `ExcelService` | Excel 文件解析与数据提取 |
| `DocumentParserService` | 文档解析（TXT/PDF/Word），支持多格式向量化 |

### 前端 (Vue 3)

| 组件 | 说明 |
|------|------|
| `FileUpload` | Excel 文件上传与任务创建 |
| `Dashboard` | 任务列表与进度监控 |
| `ReviewWorkbench` | 人工审核工作台，结果复核与风险标注 |
| `AiAssistant` | AI 智能问答助手（支持 RAG 对话） |
| `DataMonitor` | MySQL 数据库监控面板 |
| `LogisticsManagement` | 物流订单管理 |

### LangGraph 多智能体 (Python)

| 节点 | 职责 |
|------|------|
| **CoordinatorAgent** | 协调员，负责状态路由与决策分发 |
| **ExcelAgent** | 解析 Excel 文件，提取物流单据数据 |
| **OcrAgent** | 下载图片链接，调用 PaddleOCR 进行文字识别 |
| **ThinkAgent** | 调用 DeepSeek LLM，执行重量一致性校验分析 |

多 Agent 工作流执行顺序：`Coordinator → Excel → Coordinator → OCR → Coordinator → Think → Coordinator → END`

## 技术栈

### 后端
- **框架**: Spring Boot 3.2.2, Spring WebFlux
- **数据库**: MySQL 8.0+, MyBatis-Plus
- **缓存**: Redis（热点缓存 + 分布式锁）
- **消息队列**: RabbitMQ（任务分发 + 死信队列）
- **AI 框架**: LangChain4j（Java），LangGraph（Python）
- **LLM**: DeepSeek Chat（主模型），Ollama（Embedding）
- **向量数据库**: Milvus（RAG 文档存储）
- **OCR**: PaddleOCR（Python 服务）
- **认证**: JWT
- **文档**: SpringDoc OpenAPI (Swagger)

### 前端
- **框架**: Vue 3
- **路由**: Vue Router
- **HTTP 客户端**: Axios
- **构建工具**: Vite

### AI 服务
- **Coze 工作流**: 通过 Coze API 调用审核工作流
- **LangGraph 多智能体**: 本地 Python Flask 服务，端口 5000
- **RAG 检索增强**: 文档切片 → Embedding 向量化 → Milvus 存储 → 语义检索

### 部署
- **Web 服务器**: Nginx 1.14.2（反向代理 + 静态资源）
- **运行环境**: Java 17+, Python 3.10+, Node.js 16+

## 核心功能

1. **用户认证**
   - 注册/登录，JWT 令牌验证
   - 用户信息管理

2. **Excel 文件处理**
   - 上传 Excel 文件，解析物流单据数据
   - 支持大数据量分批处理（每批 8 行）

3. **AI 双引擎审核**
   - **Coze 工作流**：调用云端审核工作流，处理大批量数据
   - **LangGraph 多智能体**：本地执行 Excel 解析 → OCR 识别 → LLM 重量校验

4. **图片 OCR 识别**
   - 自动下载装卸货图片链接
   - PaddleOCR 提取图片文字，获取毛重/皮重/净重

5. **人工审核工作台**
   - 复核 AI 审核结果
   - 支持审核状态流转：未审核 → 审核中 → 已确认 / 已驳回
   - 风险等级标注：低 / 中 / 高 / 严重
   - 标签分类与备注

6. **RAG 智能问答**
   - 上传文档构建知识库
   - 文档向量化存储（Milvus）
   - 基于上下文的语义检索问答

7. **异步任务处理**
   - RabbitMQ 消息队列分发任务
   - 幂等性消费与死信队列
   - 任务超时自动关闭（8 分钟）

8. **数据库监控**
   - 实时监控 MySQL 连接池、慢查询
   - 数据库性能指标可视化

## 项目结构

```
CheckByAi/
├── Check_Ai/                       # 后端 Spring Boot 项目
│   ├── src/main/java/com/checkai/
│   │   ├── config/                 # 配置类（AI/RabbitMQ/Redis/MyBatis/JWT...）
│   │   ├── controller/             # API 控制器
│   │   ├── dto/                    # 数据传输对象
│   │   ├── entity/                 # 实体类
│   │   ├── interceptor/            # 拦截器（JWT）
│   │   ├── mapper/                 # MyBatis 数据访问
│   │   ├── scheduler/              # 定时任务（超时处理）
│   │   ├── service/                # 业务逻辑
│   │   └── util/                   # 工具类
│   └── pom.xml
├── Langchain_app/                  # LangGraph 多智能体 Python 服务
│   └── AgentsApp_langgraph.py      # 多 Agent 工作流（Coordinator/Excel/OCR/Think）
├── check_ai_web/                   # 前端 Vue 3 项目
│   ├── src/
│   │   ├── components/             # 页面组件
│   │   ├── config/                 # API 配置
│   │   └── router/                 # 路由配置
│   └── package.json
├── sql/                            # 数据库脚本
│   ├── full_schema.sql             # 完整数据库初始化
│   ├── task_review.sql             # 审核表
│   └── optimization_indexes.sql    # 索引优化脚本
├── nginx-1.14.2/                   # Nginx 配置
└── start.bat                       # 启动脚本
```

## 快速开始

### 1. 环境准备

- **Java**: JDK 17+
- **Maven**: 3.6+
- **Node.js**: 16+
- **Python**: 3.10+
- **MySQL**: 8.0+
- **Redis**: 6.0+
- **RabbitMQ**: 3.12+
- **Milvus**: 2.3+（可选，用于 RAG）
- **Ollama**:（可选，用于本地 Embedding）

### 2. 数据库初始化

```bash
mysql -u root -p < sql/full_schema.sql
```

脚本会自动创建 `check_ai` 数据库，并初始化以下表：
- `user`: 用户表
- `task`: 任务表
- `callback_data`: 回调数据表
- `task_review`: 审核表
- `logistics_order`: 物流订单表

同时会插入测试用户数据：
- 用户名: admin, 密码: 123456
- 用户名: test, 密码: 123456
- 用户名: user1, 密码: 123456

### 3. 后端配置

```yaml
# application.yml 关键配置
checkai:
  ai:
    deepseek:
      api-key: ${DEEPSEEK_API_KEY}
      base-url: https://api.deepseek.com
      model: deepseek-chat
    ollama:
      base-url: http://localhost:11434
      embedding-model: nomic-embed-text
    milvus:
      host: localhost
      port: 19530
      collection-name: checkai_documents
    rag:
      max-segment-size: 500
      max-results: 5
      min-score: 0.7
  workflow:
    api-url: ${CHECKAI_WORKFLOW_API_URL:https://api.coze.cn/v3/chat}
    bot-id: ${CHECKAI_WORKFLOW_BOT_ID}
    authorization: ${CHECKAI_WORKFLOW_AUTHORIZATION}
  langchainAgentApi: ${LANGCHAIN_AGENT_API:http://localhost:5000/api/process}

spring:
  rabbitmq:
    host: ${RABBITMQ_HOST:localhost}
    port: ${RABBITMQ_PORT:5672}
    username: ${RABBITMQ_USER:guest}
    password: ${RABBITMQ_PASS:guest}
```

### 4. 构建和运行

#### 后端

```bash
cd Check_Ai
mvn clean package -DskipTests
java -jar target/Check_Ai-0.0.1-SNAPSHOT.jar
```

#### LangGraph 多智能体服务

```bash
cd Langchain_app
pip install flask pandas paddleocr langgraph langchain-openai requests
python AgentsApp_langgraph.py
# 服务启动在 http://localhost:5000
```

#### 前端

```bash
cd check_ai_web
npm install
npm run dev
```

#### Nginx 部署

```bash
cd check_ai_web && npm run build
cp -r dist/* ../nginx-1.14.2/html/
cd ../nginx-1.14.2 && start nginx.exe
```

## API 文档

后端 API 文档通过 SpringDoc OpenAPI 生成：

```
http://localhost:8080/swagger-ui.html
```

### 主要 API 端点

| 分类 | 端点 | 说明 |
|------|------|------|
| 认证 | `POST /api/auth/login` | 用户登录 |
| 认证 | `POST /api/auth/register` | 用户注册 |
| 文件 | `POST /api/upload-excel` | 上传 Excel 文件 |
| 任务 | `GET /api/tasks` | 获取任务列表 |
| 任务 | `GET /api/task/{taskId}/results` | 获取任务结果 |
| 回调 | `POST /api/callback` | Coze 工作流回调 |
| 审核 | `POST /api/review/save` | 保存审核结果 |
| 审核 | `GET /api/review/workbench` | 获取审核工作台列表 |
| AI | `POST /ai/chat` | AI 对话 |
| AI | `POST /ai/chat/rag` | RAG 增强对话 |
| AI | `POST /ai/document/upload` | 上传文档构建知识库 |
| 物流 | `GET /api/logistics/page` | 物流订单分页查询 |
| 监控 | `GET /api/monitor/dashboard` | 数据库监控面板 |

## 环境变量参考

| 变量名 | 说明 | 默认值 |
|--------|------|--------|
| `DB_URL` | 数据库连接 URL | `jdbc:mysql://localhost:3306/check_ai` |
| `DB_USERNAME` | 数据库用户名 | `root` |
| `DB_PASSWORD` | 数据库密码 | `root` |
| `REDIS_HOST` | Redis 地址 | `localhost` |
| `REDIS_PORT` | Redis 端口 | `6379` |
| `RABBITMQ_HOST` | RabbitMQ 地址 | `localhost` |
| `RABBITMQ_PORT` | RabbitMQ 端口 | `5672` |
| `DEEPSEEK_API_KEY` | DeepSeek API 密钥 | - |
| `CHECKAI_WORKFLOW_API_URL` | Coze 工作流 API 地址 | `https://api.coze.cn/v3/chat` |
| `CHECKAI_WORKFLOW_BOT_ID` | Coze Bot ID | - |
| `CHECKAI_WORKFLOW_AUTHORIZATION` | Coze 授权令牌 | - |
| `LANGCHAIN_AGENT_API` | LangGraph 服务地址 | `http://localhost:5000/api/process` |
| `JWT_SECRET` | JWT 密钥 | `change-me` |

## 优化与演进文档

- `docs/项目优化文档.md`
- `docs/RabbitMQ引入指南.md`
- `docs/Redis热点缓存优化方案.md`
- `docs/SQL表结构与索引优化建议.md`