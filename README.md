# AI Check System

AI Check System 是一个基于人工智能的检查系统，用于处理和分析Excel文件中的数据，通过工作流进行处理并返回结果。

## 项目结构

```
CheckByAi/
├── Check_Ai/              # 后端Spring Boot项目
│   ├── src/main/java/com/checkai/  # 后端源代码
│   ├── src/main/resources/         # 配置文件
│   ├── pom.xml                     # Maven依赖配置
│   └── target/                     # 构建输出目录
├── check_ai_web/          # 前端Vue项目
│   ├── src/                        # 前端源代码
│   ├── public/                     # 静态资源
│   ├── package.json                # npm依赖配置
│   └── vite.config.js              # Vite配置
├── sql/                    # 数据库脚本
│   └── full_schema.sql             # 完整的数据库初始化脚本
└── nginx-1.14.2/           # Nginx服务器配置
    ├── conf/                       # Nginx配置文件
    └── html/                       # 静态文件目录
```

## 技术栈

### 后端
- **框架**: Spring Boot 3.2.2
- **数据库**: MySQL
- **ORM**: MyBatis Plus
- **缓存**: Redis
- **认证**: JWT
- **文档**: SpringDoc OpenAPI
- **Excel处理**: Apache POI
- **HTTP客户端**: Spring WebFlux
- **构建工具**: Maven

### 前端
- **框架**: Vue 3
- **路由**: Vue Router
- **HTTP客户端**: Axios
- **构建工具**: Vite

### 部署
- **Web服务器**: Nginx 1.14.2
- **运行环境**: Java 17+

## 核心功能

1. **用户认证**
   - 用户注册和登录
   - JWT令牌验证
   - 用户信息管理

2. **Excel文件处理**
   - 上传Excel文件
   - 解析Excel数据
   - 分批次处理大数据量

3. **任务管理**
   - 任务创建和状态跟踪
   - 任务进度监控
   - 任务超时处理

4. **工作流集成**
   - 工作流回调接口
   - 支持长文本数据处理
   - 回调数据存储和管理

5. **结果查询**
   - 任务列表查询
   - 任务结果详情查询
   - 原始任务结果汇总

## 快速开始

### 1. 环境准备

- **Java**: JDK 17+
- **Maven**: 3.6+
- **Node.js**: 16+
- **npm**: 8+
- **MySQL**: 8.0+
- **Redis**: 6.0+ (可选，用于缓存)

### 2. 数据库初始化

1. 执行SQL脚本初始化数据库：

```bash
mysql -u root -p < sql/full_schema.sql
```

2. 脚本会自动创建 `check_ai` 数据库，并初始化以下表：
   - `user`: 用户表
   - `task`: 任务表
   - `callback_data`: 回调数据表

3. 同时会插入测试用户数据：
   - 用户名: admin, 密码: 123456
   - 用户名: test, 密码: 123456
   - 用户名: user1, 密码: 123456

### 3. 后端配置

1. 修改后端配置文件/环境变量（推荐用环境变量注入敏感信息）：

```yaml
# Check_Ai/src/main/resources/application.yml（已改为支持环境变量注入）
server:
  port: 8080

spring:
  datasource:
    url: ${DB_URL:jdbc:mysql://localhost:3306/check_ai?...}
    username: ${DB_USERNAME:root}
    password: ${DB_PASSWORD:root}
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD:}

jwt:
  secret: ${JWT_SECRET:change-me}
  expiration: 86400000

# 工作流配置
checkai:
  workflow:
    api-url: ${CHECKAI_WORKFLOW_API_URL:https://api.coze.cn/v3/chat}
    bot-id: ${CHECKAI_WORKFLOW_BOT_ID:}
    authorization: ${CHECKAI_WORKFLOW_AUTHORIZATION:}
  langchainAgentApi: ${LANGCHAIN_AGENT_API:http://localhost:5000/api/process}

```

### 4. 前端配置

1. 配置前端API地址（通过环境变量）：

```javascript
// check_ai_web/src/config/api.js
export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';
```

本地示例：`check_ai_web/env.example`（复制为你本机 `.env.local` 使用）

### 5. 构建和运行

#### 后端构建

```bash
cd Check_Ai
mvn clean package
```

#### 后端运行

```bash
java -jar target/Check_Ai-0.0.1-SNAPSHOT.jar
```

#### 前端构建

```bash
cd check_ai_web
npm install
npm run build
```

#### 前端运行（开发模式）

```bash
cd check_ai_web
npm run dev
```

### 6. Nginx配置

1. 将前端构建结果复制到Nginx的html目录：

```bash
cp -r check_ai_web/dist/* nginx-1.14.2/html/
```

2. 修改Nginx配置文件：

```nginx
# nginx-1.14.2/conf/nginx.conf
server {
    listen       80;
    server_name  checkbyai.free.idcfengye.com;

    location / {
        root   html;
        index  index.html index.htm;
        try_files $uri $uri/ /index.html;
    }

    location /api {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    error_page   500 502 503 504  /50x.html;
    location = /50x.html {
        root   html;
    }
}
```

3. 启动Nginx：

```bash
cd nginx-1.14.2
start nginx.exe
```

## API文档

后端API文档使用SpringDoc OpenAPI生成，可通过以下地址访问：

```
http://localhost:8080/swagger-ui.html
```

### 主要API端点

#### 认证相关
- `POST /api/auth/register` - 用户注册
- `POST /api/auth/login` - 用户登录

#### 文件处理相关
- `POST /api/upload-excel` - 上传Excel文件

#### 任务相关
- `GET /api/tasks` - 获取任务列表
- `GET /api/task/{taskId}/results` - 获取任务结果
- `GET /api/task/original/{originalTaskId}/results` - 获取原始任务结果

#### 工作流相关
- `POST /api/callback` - 工作流回调接口（支持长文本数据）
- `GET /api/test` - 测试接口

## 前端使用说明

1. **登录系统**
   - 打开系统地址：`http://checkbyai.free.idcfengye.com`
   - 输入用户名和密码登录
   - 测试账号：admin/123456

2. **上传Excel文件**
   - 在首页点击"选择Excel文件"按钮
   - 选择要上传的Excel文件
   - 点击"上传"按钮开始处理

3. **查看任务列表**
   - 上传完成后，任务会显示在左侧任务列表中
   - 点击任务查看详细结果
   - 点击"刷新"按钮获取最新任务状态

4. **查看任务结果**
   - 点击任务列表中的任务
   - 右侧会显示该任务的详细结果
   - 结果以JSON格式展示

## 常见问题

### 1. 上传Excel文件失败

**原因**：
- 文件格式不正确（只支持.xlsx和.xls格式）
- 文件大小超过限制
- 网络连接问题

**解决方案**：
- 确保文件格式正确
- 拆分大型Excel文件为多个小文件
- 检查网络连接

### 2. 任务处理超时

**原因**：
- 数据量过大
- 工作流处理时间过长
- 系统资源不足

**解决方案**：
- 减少单次处理的数据量
- 优化工作流处理逻辑
- 增加系统资源

### 3. 回调数据处理失败

**原因**：
- 数据格式不正确
- 数据长度超过限制
- 网络连接问题

**解决方案**：
- 确保数据格式正确
- 使用POST请求并将数据放在请求体中
- 检查网络连接

## 技术支持

如有任何问题或建议，请联系：

- 邮箱：support@example.com
- 电话：13800138000

## 许可证

本项目采用MIT许可证。详见LICENSE文件。

## 优化与演进文档

- `docs/项目优化文档.md`
- `docs/RabbitMQ引入指南.md`
- `docs/Redis热点缓存优化方案.md`
- `docs/SQL表结构与索引优化建议.md`
