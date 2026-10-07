# MindHarbor 后端服务 · assistant-backend

心理健康助手 **MindHarbor（心岛）** 的服务端。提供账号认证、AI 情绪陪伴对话、心理测评、情绪日记、心理科普、知识库问答、消息通知与后台管理能力。

> 前端仓库：[assistant-web](https://github.com/xingxing-a11y/mental-health-assistant-web)（Vue 3 + Vite）

---

## 技术栈

| 维度 | 选型 |
| --- | --- |
| 语言 / 运行时 | Java 17 |
| 框架 | Spring Boot 3.4.1 |
| 持久层 | MyBatis-Plus 3.5.7（23 个 Mapper 接口，其中 7 个含手写 XML SQL） |
| 数据库 | MySQL 8（唯一持久化主库，22 张表） |
| 缓存 | Redis 7（只存可重建数据，db0，键前缀 `mindharbor:cache:`） |
| 向量库 | Chroma（Spring AI `VectorStore`，承载知识库检索） |
| AI | Spring AI 1.0.0（OpenAI 兼容接口，对话 + Embedding） |
| 认证 | java-jwt 4.4.0 + Spring Security，令牌只放 `HttpOnly` Cookie |
| 接口文档 | springdoc-openapi 2.7.0（OpenAPI 3.0.3） |
| PDF 导出 | OpenPDF 2.0.3（LGPL/MPL 许可，回避 AGPL） |
| 对象存储 | 本地磁盘 / 阿里云 OSS（同一接口两套实现，按配置互斥装配） |
| 构建 | Maven（spring-boot-maven-plugin） |

## 项目规模

| 指标 | 数值 |
| --- | --- |
| Java 主源码 | 199 个文件 / 10,337 行 |
| REST 端点 | 97 个 |
| 业务模块 | 11 个 |
| 数据库表 | 22 张，另有 17 个增量迁移脚本 |
| 单元测试 | 64 条用例 / 12 个测试类 / 1,907 行 |

## 功能模块

`com.mindharbor.modules` 下按业务垂直分包，每个模块内部统一为 `controller / service / mapper / entity / dto / vo`：

| 模块 | 能力 |
| --- | --- |
| `user` | 注册、登录、登出、资料与密码修改、头像上传 |
| `chat` | AI 会话（SSE 流式）、消息与收藏、敏感词检测与词库管理、会话导出、会话清理任务 |
| `assessment` | 心理量表、题目与选项、答题评分、报告生成、收藏与结果对比 |
| `diary` | 情绪日记记录与检索 |
| `article` | 心理科普文章（公开列表 + 后台维护） |
| `knowledge` | 知识库文档入库、向量化、重建向量 |
| `notification` | 站内通知、后台推送、过期清理任务 |
| `relax` | 放松工具数据（呼吸、冥想、压力打卡记录） |
| `feedback` | 用户反馈工单 |
| `admin` | 数据概览、用户/内容管理、操作日志 |
| `system` | 系统设置、政策说明、公告 |

公共设施在 `com.mindharbor.common`（统一响应、异常、分页、缓存、文件存储），基础设施配置在 `com.mindharbor.config`（Security、JWT、调度、密码编码）。

---

## 快速开始

### 1. 前置依赖

- **JDK 17**（`java -version` 必须是 17；JDK 21+ 下 Lombok 会静默失效，表现为满屏「找不到符号」）
- **Maven 3.9+**
- **MySQL 8**、**Redis 7**（必需）
- **Chroma 1.0.0**（可选：检索失败只降级为「无知识库上下文」，不影响登录与对话）
- 一个 **OpenAI 兼容的模型服务**（需要 chat 与 embedding 两个模型名）

### 2. 建库并导入结构

```bash
mysql -u root -p -e "CREATE DATABASE mindharbor DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
mysql -u root -p mindharbor < src/main/resources/db/schema.sql
```

- `db/schema.sql` 是**全量建表脚本**，新库执行它即可。
- `db/migrations/` 是 17 个**增量迁移脚本**（`V20260913_01__*.sql` 这类命名），只用于升级已有数据库，按文件名顺序手工执行。

### 3. 配置环境变量

复制 `.env.example` 为 `.env` 并填写。`.env` 已在 `.gitignore` 中，不会被提交。

**最小必填集：**

| 变量 | 说明 |
| --- | --- |
| `DB_USERNAME` / `DB_PASSWORD` | 数据库账号 |
| `DB_URL` | 缺省为 `jdbc:mysql://localhost:3306/mindharbor?...` |
| `JWT_SECRET` | 至少 32 字节随机串，用于签发令牌 |
| `AI_BASE_URL` / `AI_API_KEY` | 模型服务地址与密钥 |
| `AI_MODEL` / `AI_EMBEDDING_MODEL` | 对话模型名 / 向量模型名 |

**按需配置：**

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` | `localhost` / `6379` / 空 | Redis 连接 |
| `COOKIE_SECURE` | `false` | 线上走 HTTPS 时应置 `true` |
| `CHROMA_HOST` / `CHROMA_PORT` | `http://localhost` / `8000` | 向量库地址，`host` 必须带协议 |
| `STORAGE_TYPE` | `oss` | `local` 写本地磁盘，`oss` 写阿里云 OSS |
| `AVATAR_UPLOAD_DIR` | `./uploads/avatars` | `local` 模式的落盘目录，相对进程工作目录 |
| `OSS_ENDPOINT` / `OSS_BUCKET` / `OSS_ACCESS_KEY_ID` / `OSS_ACCESS_KEY_SECRET` | 空 | `oss` 模式必填，缺失则**启动失败**并列出缺项 |
| `EXPORT_PDF_FONT_PATH` | 空 | 导出 PDF 用的中文字体绝对路径，留空则中文会乱码 |
| `JOB_SESSION_CLEANUP_ENABLED` / `JOB_NOTIFICATION_CLEANUP_ENABLED` | `true` | 两个清理任务的开关 |

其余可调项（缓存 TTL、RAG 分块、导出并发、定时任务周期等）见 `src/main/resources/application.yml`，每一项都带 `${ENV_VAR:默认值}` 形式的环境变量覆盖入口。

### 4. 启动

```bash
# 开发期直接运行
mvn spring-boot:run

# 或打包后运行
mvn clean package -DskipTests
java -jar target/assistant-backend-0.0.1-SNAPSHOT.jar
```

服务监听 **8080**，业务前缀为 `/api/v1`（由 `server.servlet.context-path` 决定），因此真实 URL 形如：

```
http://localhost:8080/api/v1/auth/login
```

### 5. 验证

```bash
# 探活：Swagger UI 页面能打开即说明应用起来了
curl -I http://localhost:8080/api/v1/swagger-ui/index.html

# 注册一个账号
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"username":"demo_user","password":"demo1234"}'
```

管理员功能需要手工授权（项目不带种子数据）：

```sql
UPDATE users SET role = 'ADMIN' WHERE username = 'demo_user';
```

---

## 接口文档

| 文件 | 定位 |
| --- | --- |
| [docs/openapi.yaml](docs/openapi.yaml) | **接口的权威定义**（OpenAPI 3.0.3，97 个端点，`servers.url: /api/v1`） |
| [docs/API.md](docs/API.md) | 面向开发者的补充说明：基础约定、接口清单、SSE 协议、部署与缓存配置 |
| `/api/v1/swagger-ui/index.html` | 运行时在线调试页面 |

**基础约定：**

- 统一响应体 `{ code, message, data }`，HTTP 状态码与业务 `code` 分开。
- 状态码语义：`400` 参数/业务校验失败、`401` 未登录或令牌无效、`403` 无权限、`404` 资源不存在、`409` 状态已被并发修改（需重新拉取）、`429` 重资源操作并发超限（当前用于会话导出）、`500` 服务器异常。
- 无需登录的接口：注册、登录、公开文章、公开公告、公开量表列表与详情、系统政策说明、文档页面；其余接口都要带 `mh_token` Cookie。

---

## 架构与关键设计

整体链路：`Controller → Service → Mapper`，**业务判断只写在 Service 层**，Mapper 只负责数据访问。

### 认证链路

- 登录成功后由服务端把 JWT 写进 `HttpOnly` + `SameSite=Lax` 的 Cookie，**前端拿不到也不保存令牌字符串**，因此不存在 XSS 直接窃取令牌的路径。
- 接口是否可访问由 Spring Security 统一裁决（`config/SecurityConfig`）：放行注册/登录/公开内容，`/admin/**` 要求 `ROLE_ADMIN`，其余一律要求已登录。
- `JwtAuthenticationFilter` 校验令牌后写入安全上下文；MVC 拦截器不再决定权限，只补充安全响应头、登录限流与当前用户信息。
- 当前用户身份只从服务端上下文取（`CurrentUserProvider`），**身份类字段永不接受前端传值**；同理所有时间字段由服务端生成。
- Cookie 名唯一来源是 `AuthCookie.NAME`，写与清的唯一入口是 `JwtCookieWriter`，避免散落的字面量造成不一致。

### 缓存

**不用 Spring Cache 注解，而是手写 Redis 旁路缓存**：MySQL 始终是唯一主库，Redis 里只放可重建的派生数据。

- 键前缀统一 `mindharbor:cache:`，缓存值一律是 `*CacheEntry` record（不缓存实体对象）。
- 读路径：先查缓存 → 未命中走单飞锁（`tryLock(100ms)`，锁内再查一次）→ 仍为空才查库并回填，避免缓存击穿时打垮数据库。
- 写路径：写库后删缓存；`CacheService.deleteAfterCommit` 会判断当前是否有事务——**有事务就挂到 `afterCommit`，事务回滚则不删**，防止回滚后缓存被误清。
- Redis 不可达时快速失败并回退 MySQL（连接超时默认 2s），缓存故障不影响正确性。

### RAG 上下文组装

AI 对话每次请求会组装三段上下文交给模型（`ChatServiceImpl.buildKnowledgeContext`）：

1. **知识库参考** —— 从 Chroma 做相似度检索，取前 3 篇正文，空行拼接；检索抛异常时降级为空。
2. **对话模式** —— 按会话的 `mode` 在「理性分析」与「温柔陪伴」之间二选一。
3. **历史对话** —— 该会话最近 6 条消息。

三段拼好后注入 prompt 模板，形成「当前时间 / 参考资料 / 用户问题」的最终消息。`VectorStore` 全部用 `@Lazy` 注入，Chroma 未就绪不会阻塞应用启动。

### 敏感词三级处置

- `HIGH`：判定为高危，**立即中断本次生成**并标注会话为高风险，前端切换到危机干预提示。
- `CARE`：不中断，但写入 `sensitive_events` 事件表供后台审阅。
- `NONE`：正常放行。

词库启用了 Redis 缓存（TTL 20 分钟 + 随机抖动），后台增删改后失效缓存。

### 文件存储（头像）

- 业务层只依赖 `AvatarStorage` 接口，`local` / `oss` 两个实现由 `storage.type` **条件装配、互斥存在**。
- `storage.type` 配成非法值、或选 `oss` 但参数不全时**直接在启动期抛异常**，而不是等到第一次请求才失败——用「启动就炸」换「绝不带病运行」。
- OSS 使用 V4 签名（新建 bucket 已不支持 V1），region 从 endpoint 自动推导。
- 文件长度**由服务端边读边计数实测**，不信任请求声明的 `contentLength`，避免声明与实际不一致导致对象损坏。
- 文件名由服务端以 UUID 生成并校验形态，落盘路径做二次父目录校验，防目录穿越。
- `users.avatar_url` 只存相对路径，两种存储对外 URL 完全一致，因此**换存储后端前端与数据库都不用动**。

### 会话导出

- 返回 `ChatExportFileVO{filename, contentType, @JsonIgnore content}`，由 Controller 写入响应头与响应体。
- PDF 渲染前置 `Semaphore` 并发闸门，**拿不到许可直接返回 429**，防止少量并发把内存吃满。
- 中文字体三级兜底：配置路径 → classpath → 系统字体；只缓存字体**字节数组**，`BaseFont` 每次新建（OpenPDF 的 `BaseFont` 非线程安全）。

### 定时清理任务

全项目仅两个，放在各模块的 `job/` 包下，默认开启：

| 任务 | 默认触发 | 作用 |
| --- | --- | --- |
| `SessionCleanupJob` | 每日 03:00 | 为选择「30 天保留」的用户分批删除到期消息，并清理因此变空的会话 |
| `NotificationCleanupJob` | 每日 03:00:30 | 删除超过 180 天保留期的通知 |

两者都按 `batch-size=200` 分批、各带轮次上限防止异常数据长期占住调度线程，单批失败只记日志不中断整轮。触发时间按「环境变量 > `application.yml` > 注解兜底值」三级取值，**平时生效的是 `application.yml`**。

### 事务边界

全项目 11 处 `@Transactional`，全部落在最外层 Service 方法上，调用方都是零注解的 Controller，且事务内没有任何远程调用——因此语义清晰统一为「方法返回即提交」。需要与事务提交顺序绑定的副作用（删缓存）统一走 `afterCommit` 回调。

---

## 测试

```bash
mvn test
```

基线 **64 条用例 / 12 个测试类**，全绿，秒级完成。

设计取向是**不启动 Spring 容器**（项目内禁止 `@SpringBootTest`），全部手工装配 + 动态代理造假实现，做法有三类：

1. 配置类装配验证：`AnnotationConfigApplicationContext` 直接加载配置类。
2. Service 单元测试：`new XxxServiceImpl(mapper, ...)`，依赖用 `Proxy.newProxyInstance` 造假实现。
3. Web 层装配验证：`AnnotationConfigWebApplicationContext` + `MockServletContext` 检查拦截器/过滤器装配。

其中 `PaginationOffsetOverflowTest`（9 条）是针对性回归测试，专守一个真实缺陷：分页 offset 参与乘法时必须用 `long`，否则页长 50 时 `page ≥ 42,949,673` 会整型溢出导致 SQL 报错。

---

## Docker 部署

仓库根目录的 [docker-compose.yml](docker-compose.yml) 编排五个服务，面向 2 vCPU / 2 GB 的小内存机器（各服务都显式设了内存上限与 JVM 堆参数）：

| 服务 | 镜像 | 内存上限 |
| --- | --- | --- |
| `mysql` | `mysql:8.0` | 320M |
| `redis` | `redis:7-alpine` | 48M |
| `chroma` | `chromadb/chroma:1.0.0` | 320M |
| `backend` | `eclipse-temurin:17-jre-jammy` | 640M |
| `nginx` | `nginx:alpine`（对外 80 端口） | 48M |

**部署目录需要把两个仓库的产物拼到一起**（compose 用相对路径引用前端产物）：

```
/opt/mindharbor/
├── docker-compose.yml     # 来自本仓库
├── .env                   # 来自本仓库 .env.example
├── nginx.conf             # 来自前端仓库 assistant-web/nginx.conf
├── backend/app.jar        # 本仓库 mvn package 的产物
├── web/dist/              # 前端仓库 npm run build 的产物
├── uploads/               # 头像落盘目录（local 模式）
└── data/                  # mysql / redis / chroma 的数据卷（首次启动自动创建）
```

启动：

```bash
docker compose up -d
docker compose ps
docker compose logs -f backend
```

部署要点：

- Chroma 镜像**必须钉具体版本**（`1.0.0`），2.x 与 Spring AI 1.0.0 的 API 不兼容。
- Chroma 数据目录必须挂到容器内 `/data`（1.0 起的数据目录），挂错不会报错，但重建容器即丢向量数据。
- 导出 PDF 需要中文字体：把宿主机 `/usr/share/fonts` 只读挂进容器，并在宿主机安装 CJK 字体（如 `fonts-noto-cjk`）。
- 生产环境应配置反向代理转发头（`forward-headers-strategy: framework`），否则后端只能看到网关 IP。参见「已知限制」。

---

## 目录结构

```
assistant-backend/
├── docs/
│   ├── openapi.yaml              # 接口权威定义（OpenAPI 3.0.3）
│   └── API.md                    # 开发补充说明（SSE、部署、缓存）
├── src/main/java/com/mindharbor/
│   ├── AssistantApplication.java
│   ├── common/                   # 统一响应、异常、分页、缓存、文件存储
│   ├── config/                   # Security、JWT、调度、密码编码
│   └── modules/                  # 11 个业务模块，每个内部分层一致
│       └── <模块>/{controller,service,mapper,entity,dto,vo}
├── src/main/resources/
│   ├── application.yml           # 全部配置项，逐项带环境变量覆盖入口
│   ├── db/schema.sql             # 全量建表
│   ├── db/migrations/            # 17 个增量迁移脚本
│   └── mapper/                   # 7 个含手写 SQL 的 MyBatis 映射
├── src/test/java/                # 12 个测试类，不启动 Spring 容器
├── docker-compose.yml
├── .env.example
└── pom.xml
```

---

## 已知限制

以下均为已知事实，按影响排序，便于评估与接手：

1. **`spring-ai` 使用 `1.0.0-SNAPSHOT`**（`pom.xml`）。SNAPSHOT 需要从 `repo.spring.io/snapshot` 拉取，且可能被上游清理——若某次 clone 后构建失败，优先检查这里，建议替换为正式发布版本。
2. **需要自备模型服务与字体**：没有 API Key 时对话与知识库功能不可用；不配 `EXPORT_PDF_FONT_PATH` 时导出 PDF 的中文会乱码（不阻断启动）。
3. **无 CI 流水线**：质量保障目前依赖 64 条单元测试在提交前手工执行，仓库内没有 workflow 配置。
4. **管理员角色需手工授予**：`users.role` 默认 `USER` 且不带种子数据，需自行执行 `UPDATE users SET role='ADMIN'`。
5. **反向代理下登录限流会共桶**：限流按 `request.getRemoteAddr()` 计数，若部署在 nginx 之后且未配置转发头解析，所有用户会共享同一个来源 IP，可能导致误伤。对策是开启 `forward-headers-strategy: framework`。
6. **注册与改密接口未接入限流**，当前限流只覆盖登录入口。
7. **`GET /admin/users` 为内存分页**：该接口先全量查询再在内存切片，数据量增长后会有内存与性能压力；其余 8 处分页均为数据库 `LIMIT/OFFSET`。

## 关联仓库

| 仓库 | 说明 |
| --- | --- |
| [assistant-web](https://github.com/xingxing-a11y/mental-health-assistant-web) | 前端（Vue 3 + Vite 7），本仓库的 `docker-compose.yml` 会引用它的 `dist/` 与 `nginx.conf` |
