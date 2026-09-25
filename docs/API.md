# MindHarbor 接口与部署文档

本文档描述 MindHarbor 后端接口约定、接口清单、SSE 协议，以及部署与缓存配置说明。
`openapi.yaml` 是接口的权威定义，本文档是面向开发人员的补充说明。

## 基础约定

- OpenAPI 版本：3.0.3。
- 服务前缀写在 `servers.url: /api/v1`，`paths` 只写业务路径。
- 后端真实 URL 必须包含 `/api/v1`，推荐配置 `server.servlet.context-path=/api/v1`。
- 统一响应：`{ code, message, data }`。HTTP 状态码与业务 `code` 分开。
- 登录和注册通过 `Set-Cookie` 写入 `HttpOnly` JWT Cookie；前端不接收或保存 JWT 字符串。
- `400` 表示参数校验或业务校验不通过，`401` 表示未登录或令牌无效，`403` 表示无权限（访问他人资源或普通用户调用管理员接口），`404` 表示资源不存在，`405` 表示请求方法不支持，`409` 表示资源状态已被其它请求改变、本次操作未生效（需重新拉取最新状态后再试），`429` 表示重资源操作（例如会话导出）并发超限、需要稍后重试，`500` 表示服务器异常。
- 资源不存在统一抛出 `ResourceNotFoundException` 并映射为 HTTP `404`；参数格式、必填项和业务规则校验失败映射为 `400`；访问他人资源抛出 `SecurityException` 并映射为 `403`；条件更新影响行数为 0 时抛出 `ConflictException` 并映射为 `409`；重资源操作并发超限抛出 `TooManyRequestsException` 并映射为 `429`。
  - 唯一例外：`GET /uploads/avatars/{filename}` 的 `404` 是**空响应体**而非 `{ code, message, data }`，因为该接口成功时返回二进制流、与异常处理器的 JSON 不同源。详见「用户」章节的头像说明。
- 无需登录即可访问的接口：注册、登录、公开文章、公开公告、公开量表列表与详情、系统政策说明（`GET /system/policy`）以及接口文档页面；其余接口都需要浏览器自动携带 `mh_token` HttpOnly Cookie。
- 上传文件（当前仅头像）读取地址统一为 `GET /uploads/avatars/{filename}`，由 `AvatarController` 返回二进制内容；该地址需要登录，未登录返回 `401`，未登录状态下不会读到任何图片字节。
- 管理员接口必须由后端实际校验 `ADMIN` 角色，OpenAPI 的 security 仅用于文档表达。
- `/admin/**` 请求会校验 JWT 中的 `role=ADMIN`，普通用户令牌访问管理员接口返回 `403`。
- 接口鉴权由 Spring Security 统一裁决（`com.mindharbor.config.SecurityConfig`）：只放行注册、登录、公开文章、公开公告和公开量表，`/admin/**` 要求 `ROLE_ADMIN`，其余接口一律要求已登录。
- JWT 通过 `JwtAuthenticationFilter` 校验后写入 Spring Security 安全上下文，MVC 拦截器只补充安全响应头、登录限流和当前用户信息，不再单独决定接口是否可访问。
- 当前用户身份优先取拦截器写入的请求属性，属性缺失时回退到安全上下文中的身份，新增接口不会因为漏配拦截器路径而取不到当前用户。
- 令牌只存放在 `HttpOnly` + `SameSite=Lax` 的 Cookie 中，接口无会话状态，因此关闭 CSRF 令牌、表单登录、HTTP Basic 和默认登出；鉴权失败统一返回 `401`/`403` JSON，前端不需要适配多套鉴权入口。
- 令牌合法但账号已被管理员禁用时，接口直接返回 `401` 与“账号已被禁用，请联系管理员”，前端会清除登录态；登录接口对禁用账号返回 `400`。

## 接口清单

### 认证与用户

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/auth/register` | 注册并自动登录 |
| POST | `/auth/login` | 普通用户或管理员登录 |
| POST | `/auth/logout` | 退出登录 |
| GET | `/users/me` | 查询当前用户资料 |
| PUT | `/users/me` | 修改用户名、性别、年龄 |
| POST | `/users/me/avatar` | 上传当前用户头像（JPG、PNG、WEBP，最大 2MB） |
| GET | `/uploads/avatars/{filename}` | 读取头像图片，返回二进制内容，需登录 |
| PUT | `/users/me/password` | 校验旧密码后修改密码 |

头像上传与读取说明：

- 请求为 `multipart/form-data`，字段名固定为 `file`；仅接受 `image/jpeg`、`image/png`、`image/webp`，单文件最大 2MB，超限返回 400。
- **文件长度由服务端实测，不信任请求声明的 `contentLength`**：上传时用计数流边读边累加，超过 2MB 立即中断并返回 400，再用实测字节数写入存储。这是为了避免「声明的长度与实际流长度不一致」导致对象损坏（本地会截断文件、OSS 会写出坏对象）。
- 服务端以随机 UUID 作为文件名，扩展名由 MIME 类型决定；**存到哪里由配置项 `storage.type` 决定**，两种存储对业务代码透明：
  - `local`（默认）：写入 `storage.local.directory`，默认 `./uploads/avatars`（相对后端进程启动目录，即通常在 `assistant-backend/uploads/avatars`），可用环境变量 `AVATAR_UPLOAD_DIR` 覆盖。
  - `oss`：写入阿里云 OSS 的 `storage.oss.bucket`，对象键为 `<storage.oss.key-prefix>/<文件名>`（默认 `avatars/<文件名>`）。bucket 保持私有，因此头像不会暴露成公网链接。
  - `storage.type` 只接受 `local` / `oss`，写成其它值**不会降级也不会静默失败**，启动时直接抛出「storage.type 仅支持 local 或 oss，当前值：…」。
  - `storage.type=oss` 但 OSS 参数不全，启动直接抛出「对象存储配置缺失：…」并列出缺哪几项，避免静默退回本地存储。
- 数据库 `users.avatar_url` 只保存相对路径 `/uploads/avatars/<文件名>`；访问地址为 `http://localhost:8080/api/v1/uploads/avatars/<文件名>`，由 `AvatarController` 统一读取，两种存储对外地址完全一致，前端不需要区分。
- 读头像时文件名必须符合上传接口生成的形态（UUID + `.jpg`/`.png`/`.webp`）；不合法或文件不存在都返回 404，不区分「格式错误」和「不存在」，避免用错误码探测存储布局。
- **404 是空响应体，不返回 `ApiResponse` JSON**。原因是本接口成功时返回二进制流，而全局异常处理器产出的是 JSON，两者不同源、无法互相序列化；因此 `ResourceNotFoundException` 被在控制器内收敛为无体 404，让浏览器的 `<img>` 标签能直接走 `onerror` 分支。其它接口的 404 仍是 JSON。
- 响应头带 `Cache-Control: private, max-age=3600`：文件名是随机 UUID、内容不会再变化，因此可以缓存；同时 `private` 保证图片不会被中间代理缓存给其他用户。
- 响应头同时带 `Content-Disposition: inline`（避免浏览器对未知类型弹下载框）与 `X-Content-Type-Options: nosniff`（禁止内容类型嗅探）。
- 上传成功后响应 `data` 为当前用户对象（包含新的 `avatarUrl`），并删除该用户的上一张头像文件；旧文件清理失败只记警告日志，不影响本次上传，也不会让接口报错。
- **写库使用条件更新而非整行覆盖**：`UPDATE users SET avatar_url=? WHERE id=? AND avatar_url <=> ?`（`<=>` 是 NULL 安全等于，首次上传时旧值为 NULL 也能命中）。
  - 若同一用户在本次请求处理期间已被另一个并发请求改过头像，本次更新影响行数为 0，服务端**先删除刚上传的新对象再返回 `409`**，不会覆盖对方的结果、也不会留下无引用对象。
  - 客户端收到 409 后应重新拉取用户信息展示最新头像，再决定是否重试。
- 读头像（`loadAvatar`）同样会把对象读入内存后再转发，上限 2MB。与上传路径的取舍不同：读取时持有完整字节是为了在写出响应前确认对象确实存在，避免响应中途断连导致 OSS 连接悬挂。
- 切换 `storage.type` 不会迁移历史文件：从 `local` 切到 `oss` 后，旧头像仍留在本地目录且无法再通过接口读取，需要人工迁移或让用户重新上传。

### AI 对话

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/chat/sessions` | 当前用户会话列表 |
| GET | `/chat/sessions/archived` | 当前用户已归档会话列表 |
| POST | `/chat/sessions` | 新建会话，名称可选 |
| GET | `/chat/sessions/{sessionId}/messages` | 历史消息 |
| POST | `/chat/sessions/{sessionId}/messages` | POST SSE 流式问答 |
| DELETE | `/chat/sessions/{sessionId}/context` | 只清空模型上下文，保留数据库消息 |
| DELETE | `/chat/sessions/{sessionId}` | 永久删除已归档会话和全部消息 |
| PUT | `/chat/sessions/{sessionId}/name` | 修改会话名称；新会话首次发送时默认使用首条用户消息 |
| PUT | `/chat/sessions/{sessionId}/pin` | 切换会话置顶状态 |
| PUT | `/chat/sessions/{sessionId}/archive` | 归档会话并从“我的会话”中移除 |
| PUT | `/chat/sessions/{sessionId}/unarchive` | 取消归档并恢复到“我的会话” |

### 心理测评

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/assessments/scales` | 量表列表 |
| GET | `/assessments/scales/{scaleId}` | 量表详情和题目 |
| POST | `/assessments` | 提交全部题目答案，后端校验完整性并计算基础报告 |
| POST | `/assessments/analysis` | 用户主动请求对指定测评结果进行 AI 解读和安慰建议 |
| GET | `/assessments` | 当前用户测评历史 |

测评提交时后端会校验量表、题目和选项的归属，并读取数据库中选项配置的分值计算总分；前端不得传入或覆盖分数。

测评提交成功后会写入 `assessment_results` 表；`GET /api/v1/assessments` 按当前用户倒序返回测评时间和报告摘要。

测评接口的返回值使用 VO 收敛类型（`AssessmentScaleDetailVO`、`AssessmentReportVO`、`AssessmentAnalysisVO`、`AssessmentHistoryVO`），Service 不再返回 `Object`、`Map` 或 `List<?>`：

- `GET /assessments/scales/{scaleId}` 返回 `id`、`name`、`description`、`applicableScene` 和 `questions`（每题带 `options`）。
- `POST /assessments` 返回 `resultId`、`totalScore`、`standardScore`、`rangeDescription` 和 `suggestions` 字符串数组。
- `POST /assessments/analysis` 返回 `analysis` 解读文本。
- `GET /assessments` 返回 `id`、`scaleName`、`totalScore`、`standardScore`、`rangeDescription`、`suggestions` 和 `createdAt`，不再返回 `userId`、`scaleId` 等内部标识。
- `PUT /admin/scales/{scaleId}/questions` 覆盖式维护题目时，删除旧题目与写入新题目在同一个事务中完成，中途失败会整体回滚。

报告建议统一为两条：提交时按行写入 `assessment_results.suggestions`，历史接口按同一语义还原成字符串数组；`POST /assessments/analysis` 的 AI 解读按需生成并直接返回，不写回该字段，避免覆盖原始建议。

### 心理科普

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/articles?keyword=睡眠` | 文章列表和标题搜索 |
| GET | `/articles/{articleId}` | 文章详情 |

### 管理员

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/admin/users?page=0&size=20&nickname=张` | 用户列表（分页，支持按用户名（昵称）模糊搜索） |
| PUT | `/admin/users/{userId}/status` | 启用或禁用用户；禁用后该账号的既有令牌立即失效 |
| GET | `/admin/knowledge-documents` | 知识库列表 |
| POST | `/admin/knowledge-documents` | 新增文档 |
| PUT | `/admin/knowledge-documents/{documentId}` | 编辑文档 |
| DELETE | `/admin/knowledge-documents/{documentId}` | 删除文档和旧向量 |
| POST | `/admin/knowledge-documents/{documentId}/vectorize` | 手动向量化 |
| POST | `/admin/knowledge-documents/{documentId}/re-vectorize` | 删除旧向量后重新向量化 |
| GET | `/admin/scales` | 管理员量表列表 |
| POST | `/admin/scales` | 新增量表 |
| PUT | `/admin/scales/{scaleId}` | 编辑量表名称、简介和适用场景 |
| PUT | `/admin/scales/{scaleId}/publish` | 发布量表；量表必须先配置题目，否则返回 400 |
| DELETE | `/admin/scales/{scaleId}` | 删除量表及其题目和选项；量表已有测评记录时返回 400，不删除用户历史 |
| PUT | `/admin/scales/{scaleId}/questions` | 批量维护题目，支持单选、多选、文字题、判断题和矩阵量表题；选择题可配置“其他”并填写补充内容 |
| GET | `/admin/articles` | 管理员文章列表，返回更新时间（精确到秒）和更新者用户名 |
| POST | `/admin/articles` | 发布心理科普文章 |
| PUT | `/admin/articles/{articleId}` | 编辑心理科普文章 |
| DELETE | `/admin/articles/{articleId}` | 删除心理科普文章 |

## 二期接口

以下接口属于二期迭代能力，当前后端接口和基础前端页面已实现，可在后续继续增强分页、图表和富文本编辑体验：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/diaries/recent` | 查询最近 2 条情绪日记（首页“最近记录”） |
| GET/POST | `/diaries` | 分页查询或新增情绪日记；支持 `page`、`size`（最大 50）、`startDate`、`endDate`（yyyy-MM-dd），返回 `{ items, page, size, total, totalPages }`，开始日期晚于结束日期返回 400 |
| PUT | `/diaries/{diaryId}` | 编辑本人情绪日记 |
| GET/DELETE | `/diaries/{diaryId}` | 查看或删除日记 |
| POST | `/diaries/{diaryId}/analysis` | AI 分析日记并给出疏导建议 |
| GET | `/emotions/trend` | 查询个人情绪趋势，仅本人可见 |
| PUT | `/chat/sessions/{sessionId}/settings` | 温柔陪伴/理性分析模式切换 |
| PUT | `/chat/messages/{messageId}/favorite` | 收藏或取消收藏 AI 消息 |
| GET | `/chat/messages/favorites` | 查询当前用户收藏的 AI 消息；三期起改为分页信封，字段为 `id`、`content`、`sessionId`、`createdAt`，详见「AI 对话增强」 |
| GET | `/admin/audits/high-risk` | 高危会话脱敏审计 |
| GET | `/admin/statistics/overview` | 管理员数据统计概览 |
| GET/POST | `/admin/announcements` | 查询或发布系统公告 |
| PUT | `/admin/announcements/{announcementId}` | 编辑系统公告 |
| DELETE | `/admin/announcements/{announcementId}` | 删除系统公告 |

普通用户首页可调用 `GET /announcements` 获取当前公告列表，该接口无需登录。

二期接口同样需要 JWT；管理员接口必须校验 `ADMIN` 角色。高危审计只允许展示脱敏摘要，不得返回完整聊天内容。

## 三期接口

三期新增「放松小工具」「拓展功能」「个人设置」「通知」四组用户侧能力，以及管理后台四个扩展子节。列表接口规范：分页接口统一返回 `{ items, page, size, total, totalPages }`，禁止同一 URL 因参数不同返回不同结构；不分页的小集合接口固定返回数组。

### 放松小工具

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/relax/overview` | 首页汇总：本周练习次数、本周平均压力、累计练习次数、最近一次压力打卡 |
| GET | `/relax/meditations` | 冥想引导脚本列表（3/5/10 分钟，含分段引导词） |
| GET/POST | `/relax/records` | 分页查询或记录放松练习；支持 `type`（BREATHE / MEDITATION）、`page`、`size`（最大 50） |
| GET/POST | `/relax/stress-logs` | 分页查询或记录压力打卡；`level` 取 1–10，`note` 最长 200 字 |
| GET | `/relax/stress-trend?days=30` | 近 N 天压力曲线，默认 30 天、上限 90 天，按自然日聚合平均压力 |

- 冥想引导词由后端以常量脚本返回，前端使用浏览器内置语音合成朗读，不引入真人录音素材。
- 压力趋势按自然日分组，返回 `logDate` 与 `averageLevel`；无打卡记录的日期不做补零。
- 4-7-8 呼吸计时器的计次通过练习记录的 `rounds` 字段提交，后端只做范围校验与落库。

### 测评拓展

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| PUT | `/assessments/scales/{scaleId}/favorite` | 收藏或取消收藏量表，请求体 `{ "favorite": true }` |
| GET | `/assessments/favorites` | 我的收藏量表列表 |
| GET | `/assessments/scales/{scaleId}/history` | 同一量表历次结果序列（时间、总分、标准分、区间），用于结果对比 |
| GET | `/assessments/recommendations` | 基于历史结果的量表推荐，最多 5 条，含 `reason` 与 `priority` |

- `GET /assessments/scales` 的响应在三期增加 `favorited` 字段，标记当前用户是否已收藏；该接口保持公开，未登录时 `favorited` 统一为 `false`。
- 推荐规则写在 service 层：最近一次标准分达到阈值（60）的量表优先建议复测，剩余额度优先给尚未测评过的量表。
- 收藏关系保存在 `assessment_favorites`，同一用户对同一量表只有一条记录，重复收藏不报错。

### AI 对话增强

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/chat/messages/favorites` | 收藏消息分页查询；支持 `page`、`size`（默认 0/20，最大 50）、`keyword`（内容模糊）、`sessionId` |
| DELETE | `/chat/messages/favorites/{messageId}` | 收藏页面取消收藏 |
| POST | `/chat/sensitive-check` | 发送前预校验，仅用于前端提示 |
| GET | `/chat/sessions/{sessionId}/export?format=txt\|pdf` | 导出单次会话，返回文件流与 `Content-Disposition: attachment` |

敏感词判定：

- 预校验请求体 `{ "content": "..." }`，响应 `{ triggered, level, prompt, hotline }`，`level` 为 `NONE`、`CARE` 或 `HIGH`。
- 响应只给出统一提示文案与心理援助热线，不回显命中的原始词。
- 服务端强制校验：`POST /chat/sessions/{sessionId}/messages` 与预校验接口复用同一套判定入口。
- 命中 `HIGH`：不调用模型，写入会话 `high_risk` 标记，通过 SSE `risk` 事件返回危机提示后结束。
- 命中 `CARE`：不阻断回复，仅向 `sensitive_events` 写入一条审计记录；记录内容只有 `user_id`、`session_id`、`word_id`、`level`、`created_at`，不含用户原文。
- 词表来自 `sensitive_words` 表；表为空或读取失败时回退内置词表，保证校验不会因数据问题被绕过。
- 前端预校验只用于提示，不能作为唯一校验入口；绕过前端直接调用对话主接口同样会被拦截。

会话导出：

- 导出当前用户自己的会话；会话不存在返回 404，不属于当前用户返回 403。
- TXT 使用 UTF-8 并带 BOM，避免 Windows 记事本打开中文乱码。
- PDF 使用 OpenPDF（`com.github.librepdf:openpdf`，LGPL 2.1 / MPL 2.0 双许可），不使用 AGPL 依赖。
- 导出属于重资源操作，并发超限时返回 `429` 与“导出任务繁忙，请稍后重试”；前端只提示稍后重试，不展示内部细节。

### 个人设置

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/users/me/settings` | 查询个人设置，未初始化时返回默认值 |
| PUT | `/users/me/settings` | 更新个人设置 |
| POST | `/users/me/deactivate` | 注销账号（匿名化保留业务数据），成功后清除登录 Cookie |
| GET | `/system/policy` | 隐私协议、数据用途说明、免责声明、心理援助热线与版本号（无需登录） |
| POST | `/feedbacks` | 提交反馈：`type` = BUG / SUGGESTION / OTHER，`content` ≤ 2000 字，`contact` 可选 |

设置项与默认值：

| 字段 | 取值 | 默认值 |
| --- | --- | --- |
| `defaultChatMode` | `COMPANION`（温柔陪伴）/ `ANALYTICAL`（理性分析） | `COMPANION` |
| `streamSpeed` | `SLOW` / `NORMAL` / `FAST` | `NORMAL` |
| `messageRetention` | `PERMANENT` / `THIRTY_DAYS` | `PERMANENT` |
| `dataConsent` | 布尔，是否允许匿名使用数据优化知识库 | `false` |
| `theme` | `LIGHT` / `DARK` | `LIGHT` |
| `fontScale` | `SMALL` / `MEDIUM` / `LARGE` | `MEDIUM` |
| `messageSound` | 布尔 | `true` |
| `crisisTip` | 布尔，进入对话页是否展示求助热线 | `true` |

注销账号说明：

- 注销采用匿名化保留，不物理删除会话、消息、日记、测评与收藏等业务数据。
- 单条 `UPDATE users` 完成：`status=CLOSED`、写入 `deactivated_at`、`username` 改写为 `closed_{id}`，清空 `nickname`、`avatar_url`、`gender`、`age`，并重置登录失败计数与锁定状态。
- 更新成功后删除头像文件（失败只记警告日志，不影响注销结果），并强制关闭 `data_consent`。
  - 注销后 `avatar_url` 已置空，删失败留下的对象**再无任何记录能说明归属**，因此该路径的删除日志会同时带上 `userId` 与 `filename`，便于事后人工排查。
  - 与换头像不同的是，注销路径**不使用条件更新（CAS）**：注销是用户主动发起的终态操作，即便与换头像并发，最终 `avatar_url` 为 NULL 也与「账号已注销」的语义一致，不会产生可观察的 404。详见 `UserServiceImpl.deactivate`。
- 状态不是 `ACTIVE` 的账号立即无法访问接口：禁用与注销使用不同提示文案，旧 Cookie 在令牌过期前即失效。

### 通知

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/notifications` | 本人可见通知分页列表，单页上限 50，每项含 `read` 标记 |
| GET | `/notifications/unread-count` | 未读数 `{ "count": 3 }`，供铃铛 60 秒低频轮询 |
| PUT | `/notifications/{id}/read` | 标记单条已读 |
| PUT | `/notifications/read-all` | 全部标记已读 |

- 可见性：`scope=ALL` 的全体通知，加上 `scope=USER` 且 `target_user_id` 为当前用户的通知。
- 过期过滤：`expire_at IS NULL`（永不过期）或 `expire_at > NOW()`；过期通知不出现在列表，也不计入未读数。
- 读时扇出：全体推送只写 1 条通知主记录，已读状态存放在 `notification_reads`，读取时一次 `LEFT JOIN` 取回，不为每个用户写通知记录。
- 未读数为空时返回 `0`；标记已读幂等，重复调用不报错。
- 全局通知默认 30 天过期，过期满 180 天后由归档清理任务删除，查询性能依赖 `(scope, created_at)` 与 `(target_user_id, created_at)` 索引。

### 管理后台扩展

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/admin/notifications` | 推送心理小贴士，`scope` = ALL / USER（USER 时 `userId` 必填） |
| GET | `/admin/notifications` | 推送记录分页列表 |
| DELETE | `/admin/notifications/{id}` | 撤回推送，已读记录随外键级联删除 |
| GET/POST | `/admin/sensitive-words` | 敏感词词库列表 / 新增；`word` ≤ 64 字、`level` = CARE / HIGH、`enabled` |
| PUT/DELETE | `/admin/sensitive-words/{id}` | 修改或删除词条；重复词条返回 400 |
| GET | `/admin/feedbacks` | 反馈工单分页列表，支持 `status` 过滤 |
| PUT | `/admin/feedbacks/{id}` | 处理工单：`status` = PENDING / PROCESSED / REJECTED，`remark` ≤ 500 字 |
| GET | `/admin/operation-logs` | 管理员操作审计分页查询，支持 `type`、`startDate`、`endDate` |
| GET/PUT | `/admin/knowledge-settings` | 知识库分片参数全局配置 |

推送过期时间规则：

- `expireAt` 可选，未提供时默认为 30 天后过期；`permanent=true` 表示永不过期，此时忽略 `expireAt`。
- `expireAt` 早于当前时间返回 400；定向推送时目标用户不存在返回 404。

操作日志：

- 记录点：知识库文档新增/编辑/删除、量表发布/删除、量表题目替换、科普文章新增/编辑/删除、系统公告新增/编辑/删除、用户禁用/启用、通知推送/撤回、敏感词规则新增/修改/删除、反馈工单处理、知识库分片参数更新。
- 记录内容只有操作者编号与用户名、操作类型、目标类型与编号、动作摘要和发生时间，不写业务正文。
- 审计在 service 层显式调用，不使用隐式切面；写入失败只记录 `log.error`，不影响业务操作本身。
- `type` 取值：`DOCUMENT`、`SCALE`、`ARTICLE`、`ANNOUNCEMENT`、`USER`、`NOTIFICATION`、`SENSITIVE_WORD`、`FEEDBACK`、`SYSTEM_SETTING`。

缓存策略：

- 科普文章、系统公告与敏感词列表使用 Redis 旁路缓存，MySQL 仍为唯一持久化主库。
- 缓存未命中时使用当前 JVM 内按缓存键隔离的单飞锁，避免单体实例内并发回源；未来多实例部署时需要替换为分布式锁。
- 文章详情查询不到数据时写入短 TTL 空缓存，防止不存在 ID 持续穿透数据库；文章新增时会尝试删除对应空缓存，Key 不存在属于正常情况。
- Redis 命中但 JSON 反序列化失败时先删除损坏 Key，再回源 MySQL；Redis 故障时回退 MySQL。
- 写入操作只在 MySQL 事务提交成功后的 `afterCommit` 回调中删除相关缓存；删除失败最多同步重试一次，最终依靠 TTL 恢复。
- 只有公开读取路径读写缓存（`GET /articles`、`GET /articles/{articleId}`、`GET /announcements`）；管理端科普文章列表 `GET /admin/articles` 直接查询数据库，保证管理员始终看到最新数据。
- 缓存是写后删除而不是写后更新：编辑数据只会让对应键消失，要等下一次公开读取才重新回填，因此刚改完时在 Redis 中看不到该键属于正常现象。
- 缓存键与 TTL、单飞锁与多实例迁移要求见本文档「部署与配置」章节。

知识库分片参数：

- 键名 `knowledge.chunk-size` 与 `knowledge.chunk-overlap`，保存在 `system_settings` 表。
- 未配置时回退 `application.yml` 的 `rag.chunk-size`（默认 1000）与 `rag.chunk-overlap`（默认 100）。
- `chunkSize` 取值 100–4000，`chunkOverlap` 取值 0–1000 且必须小于 `chunkSize`，否则返回 400。
- 参数在向量化时读取并覆盖配置文件默认值，修改后需重新向量化已有文档才会生效。

用户列表（`GET /admin/users`）返回 `status` 字段，`CLOSED` 表示已注销账号，其业务数据保持可查询。

### 定时清理任务

两个清理任务完全独立，各自读取配置、各自 `try-catch` 与日志前缀，一个任务异常或被关闭不影响另一个：

| 任务 | 配置前缀 | 默认周期 | 清理内容 |
| --- | --- | --- | --- |
| `SessionCleanupJob` | `job.session-cleanup` | 每日 03:00 | 保存策略为「30 天自动清理」的用户的到期消息，以及已无消息的到期会话 |
| `NotificationCleanupJob` | `job.notification-cleanup` | 每日 03:30 | 过期已满保留期（默认 180 天）的通知，已读记录随外键级联删除 |

- 周期由 `job.*.cron` 配置，开关由 `job.*.enabled` 配置，设为 `false` 可单独停用该任务。
- 删除按 `LIMIT` 分批循环执行，单批条数由 `job.*.batch-size` 控制，避免一次大事务锁表。
- 单个用户或单个批次失败只记日志并继续处理后续数据，日志前缀 `[session-cleanup]` 与 `[notification-cleanup]` 可区分来源。
- 调度线程池为 2（`spring.task.scheduling.pool.size`），`@EnableScheduling` 放在 `SchedulingConfig`。

## SSE 协议

SSE 使用 POST，前端必须使用 `fetch + ReadableStream`，不能使用原生 `EventSource`。

请求头：

```http
Content-Type: application/json
Accept: text/event-stream

浏览器通过 HttpOnly Cookie 自动携带 JWT；跨域前端请求必须设置 `credentials: include`。
```

请求体：

```json
{
  "content": "最近总是睡不好，我该怎么办？"
}
```

事件约定：

```text
event: message
data: {"content":"先从固定作息开始尝试。"}

event: ping
data: heartbeat

event: risk
data: {"level":"HIGH","message":"你提到的内容让我们很担心你。","hotline":"全国心理援助热线 12356，紧急情况请拨打 120"}

event: done
data: {"messageId":12345}

event: error
data: {"code":"AI_SERVICE_ERROR","message":"暂时无法生成回复"}
```

`ping` 只用于心跳，前端收到后丢弃；`risk` 独立展示危机提示，高风险时停止普通回复，`message` 与 `hotline` 都由服务端敏感词判定统一提供，不在前端拼接。后端必须返回 `Content-Type: text/event-stream;charset=UTF-8`，及时刷新事件，并在 Nginx 中关闭 `proxy_buffering`。

## 数据与代码规范

- MySQL 保存用户、会话、消息、量表、测评、文章和知识库数据。
- Spring AI 使用正式版依赖，但项目不预设具体 AI 模型。模型地址、API Key 和模型名称由开发者自行配置：`AI_BASE_URL`、`AI_API_KEY`、`AI_MODEL`。`AI_MODEL` 为空时不会强制选择 `gpt-4o-mini`。可通过 `AI_MAX_TOKENS`（默认 512）和 `AI_RETRY_MAX_ATTEMPTS`（默认 1）降低响应延迟。
- 密码使用 BCrypt 存储，不得记录密码、JWT、完整提问或完整 AI 回复。
- 登录和注册返回 HS256 JWT，密钥通过 `JWT_SECRET` 配置，必须使用至少 32 字节的随机值；过期时间通过 `JWT_EXPIRATION_SECONDS` 配置，生产环境不得使用开发占位密钥。登录 Cookie 的 `Max-Age` 复用同一配置，不再单独维护常量，避免改了一处、另一处仍留旧值。
- 登录连续失败 5 次后账号锁定 15 分钟；成功登录会清零失败次数。锁定期间接口返回业务错误，前端应提示用户稍后重试。
- DTO、VO 只声明数据字段，使用 Lombok，不使用 Java `record`，不写 `from` 或业务方法。
- Entity 使用 Lombok 生成访问器，业务逻辑放在 Service。
- Controller 只接收参数、记录日志并封装响应，不直接调用 Mapper；Mapper 只声明数据访问，业务判断统一放在 Service。
- 异常分层：`ResourceNotFoundException` 表示资源不存在（404），`SecurityException` 表示越权访问（403），`IllegalArgumentException` 表示参数或业务校验失败（400）；禁止用 `IllegalArgumentException` 表达资源不存在。
- `GlobalExceptionHandler` 统一处理 400/403/404/405/500，Controller 与 Service 不自行拼装错误响应结构。
- 后端按模块拆分 `controller`、`service`、`service.impl`、`mapper`、`dto`、`vo`、`entity`。
- Service 的返回值必须使用具体类型或 `vo` 包下的 VO，禁止返回 `Object`、`Map` 或 `List<?>`；Map 只允许在方法内部作为临时结构使用。
- 一个方法内包含多次写库时必须加 `@Transactional`（例如覆盖式维护量表题目），保证中断时不会留下半成品数据。
- 删除存在关联业务数据的主记录时必须先校验并返回明确的业务提示（`400`），不允许把数据库外键错误直接抛给用户。
- 账号状态校验由 `UserStatusService` 提供，Mapper 只做数据访问，禁用判断放在 service 层；安全过滤器与 MVC 拦截器复用同一请求内的校验结果，避免重复查询用户表。
- 任何安全判定（敏感词、高危内容、越权访问）必须在服务端执行；前端校验只作为提示，不得作为唯一入口。发送前预校验接口与业务主接口（对话 SSE）复用同一套服务端判定入口。
- 敏感词命中结果只返回级别与统一提示文案，不得回显命中的原始词；命中审计只记录用户编号、会话编号、词条编号、级别和时间，不记录用户原文。
- 分页接口统一返回 `{ items, page, size, total, totalPages }`；禁止同一个 URL 因参数不同返回两种结构，也禁止为分页再新增 `/page` 后缀接口。不分页的小集合接口固定返回数组，成员类型用 `$ref` 复用。
- 定时清理任务按业务独立拆分，各自捕获异常、各自打印带前缀的日志，周期可配置、可单独关闭；批量删除必须带 `LIMIT` 分批执行，避免长事务锁表。
- 账号注销采用匿名化保留：只改写用户标识并置 `status=CLOSED`，不物理删除会话、消息、日记与测评数据，也不在一个接口里做大范围删除事务。
- 会话导出 PDF 使用 OpenPDF（LGPL 2.1 / MPL 2.0）；打包成 jar 后 `resources` 内的字体是压缩包内资源，无法按文件路径读取，因此中文字体支持三级兜底（外部绝对路径 → classpath 字节流 → 系统字体），仅缓存不可变字体字节，`BaseFont` 每次导出新建。
- 字体部署建议：把中文字体放到服务器外部目录，并通过 `EXPORT_PDF_FONT_PATH`（对应 `export.pdf.font-path`）配置绝对路径；字体全部缺失时导出接口返回 400 与“未配置中文字体”。
- 前端按模块拆分 Vue 组件，`App.vue` 只负责页面级状态和模块协调。
- 管理端的破坏性操作（禁用账号、删除测评量表、删除科普文章、删除知识库文档、删除系统公告）必须先弹出二次确认弹窗，用户确认后再发起请求；确认弹窗统一复用 `ConfirmDialog.vue`，并在文案中说明影响范围与不可恢复性。
- import、字段、方法和配置项正常换行；方法之间保留空行；方法体保持层级缩进，禁止压缩成单行。
- 前端模板、脚本和 `style.css` 不得把整段结构压缩成一行；CSS 每条规则独立成行，样式声明逐行书写。
- 密码哈希、会话等关联对象必须禁止 JSON 序列化（`@JsonIgnore`），避免响应体意外带出内部数据。
- Controller 记录必要的 `log.info`，异常记录 `log.error`，不得输出敏感信息。
- `openapi.yaml` 是正式接口定义，本文档是面向开发人员的补充说明；修改接口时必须同步更新两者。

## 运行测试

后端已引入 `spring-boot-starter-test`（`test` scope），测试统一放在 `src/test/java` 下，入口只有一条命令：

```bash
cd assistant-backend
mvn test
```

首次运行需要联网拉取测试依赖（`settings.xml` 的 aliyun 镜像可满足）；依赖落地后 `mvn -o test` 可离线执行。

- **不依赖任何外部服务**：测试全部使用手工 `AnnotationConfigApplicationContext` 并只注册被测配置类，**不使用 `@SpringBootTest`**，因此不需要启动 MySQL、Redis 或 Chroma。这是刻意的约束——一旦改用 `@SpringBootTest`，`mvn test` 就不再能随时执行。
- **对象存储测试不连真实 OSS**：`OssAvatarStorageTest` 用动态代理桩（`FakeOss`）模拟服务端，不需要真实凭证，也能稳定注入 `NoSuchKey`、`AccessDenied` 等错误码来验证分类逻辑。
- **覆盖范围**：存储条件装配与非法配置的快速失败、本地磁盘读写与目录穿越防护、OSS 读写与错误码分类、头像大小上限（含 `skip()` 绕过这条越权路径）、头像读取接口的响应形态与响应头、并发冲突的 409 映射。
- **`@TempDir` 的用法**：不要把它写成 `@ParameterizedTest` 的方法参数——`TempDirectory` 扩展与参数化测试的参数解析器会争夺同一个参数位而直接报错；请使用字段级 `@TempDir`。

## 部署与配置

本项目按单体应用部署：`assistant-web` 是前端工程，`assistant-backend` 是 Spring Boot 服务，
外部依赖为 MySQL、Redis、Chroma 和一个 OpenAI 兼容的模型服务。

### 组件职责

| 组件 | 职责 | 缺失时的表现 |
| --- | --- | --- |
| MySQL | 唯一持久化主库，保存用户、会话、文章、公告、敏感词等全部业务数据 | 服务不可用 |
| Redis | 只保存可重建缓存，过期或被清空都不影响数据正确性 | 自动回退 MySQL，功能正常但每次请求都要查库 |
| Chroma | 知识库向量库，保存文档分片向量 | 知识库检索不可用，其余功能不受影响 |
| 模型服务 | 对话与向量化，OpenAI 兼容接口 | 对话与向量化不可用 |

### 环境变量

后端的外部连接全部通过环境变量注入，`application.yml` 只提供本机默认值。本地开发可以写在 `assistant-backend/.env`
（IDEA 通过 EnvFile 插件加载），生产环境必须使用真正的环境变量或配置中心。

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `DB_URL` | 本机 `mindharbor` 库 | MySQL 连接串，时区固定 `Asia/Shanghai` |
| `DB_USERNAME`、`DB_PASSWORD` | 空 | 数据库账号 |
| `REDIS_HOST`、`REDIS_PORT` | `localhost`、`6379` | Redis 地址 |
| `REDIS_PASSWORD` | 空 | Redis 密码，未启用密码时留空 |
| `REDIS_DATABASE` | `0` | Redis 逻辑库编号，便于与其他应用共用实例时隔离 |
| `REDIS_TIMEOUT` | `2s` | Redis 命令超时；调大容忍更慢的网络，调小更快回退 MySQL |
| `CHROMA_HOST`、`CHROMA_PORT` | `http://localhost`、`8000` | Chroma 地址，`CHROMA_HOST` 必须带协议 |
| `AI_BASE_URL`、`AI_API_KEY` | 空 | 模型服务地址与密钥 |
| `AI_MODEL`、`AI_EMBEDDING_MODEL` | 空 | 对话模型与向量模型名称 |
| `JWT_SECRET` | 空 | 至少 32 字节随机值，缺失或过短时启动直接失败 |
| `JWT_EXPIRATION_SECONDS` | `7200` | 令牌有效期（秒）；登录 Cookie 的 `Max-Age` 复用该值 |
| `COOKIE_SECURE` | `false` | 站点走 HTTPS 时设为 `true` |
| `STORAGE_TYPE` | `local` | 上传文件存储方式：`local` 写本地磁盘，`oss` 写阿里云 OSS |
| `AVATAR_UPLOAD_DIR` | `./uploads/avatars` | `STORAGE_TYPE=local` 时的头像保存目录，需对进程可写 |
| `OSS_ENDPOINT` | 空 | `STORAGE_TYPE=oss` 时必填，例如 `https://oss-cn-hangzhou.aliyuncs.com` |
| `OSS_BUCKET` | 空 | `STORAGE_TYPE=oss` 时必填，bucket 保持私有，不要设为公共读 |
| `OSS_ACCESS_KEY_ID`、`OSS_ACCESS_KEY_SECRET` | 空 | `STORAGE_TYPE=oss` 时必填，只从环境变量注入，不写进配置文件 |
| `OSS_KEY_PREFIX` | `avatars` | OSS 对象键前缀，头像最终存为 `<前缀>/<文件名>` |
| `EXPORT_PDF_FONT_PATH` | 空 | 会话导出使用的中文字体绝对路径 |

安全约定：

- `.env` 只保留在本地，不要提交到版本库；仓库中只提供 `.env.example` 占位符。
- 日志禁止输出文章正文、公告正文、敏感词原文、用户隐私，以及 Redis 密码等连接凭据。
- 缓存读写失败只记录异常类型，完整堆栈放在 DEBUG，避免异常信息把文章、公告或敏感词正文带进日志。
- 生产环境必须替换示例密钥，并通过环境变量注入，不写入 `application.yml`。

### 缓存键与 TTL

所有键都带 `mindharbor:cache:` 前缀，与同一 Redis 实例上的其他数据区分。

| 键 | 内容 | TTL | 失效时机 |
| --- | --- | --- | --- |
| `mindharbor:cache:article:list` | 科普文章列表 DTO | 1 小时 + 0～10 分钟随机偏移 | 文章新增、修改、删除提交后删除 |
| `mindharbor:cache:article:{id}` | 科普文章详情 DTO | 1 小时 + 0～10 分钟随机偏移 | 文章修改、删除提交后删除 |
| `mindharbor:cache:article:missing:{id}` | 空缓存，表示文章不存在 | 5 分钟 + 0～1 分钟随机偏移 | 文章新增、删除提交后尝试删除，键不存在属正常情况 |
| `mindharbor:cache:announcement:list` | 系统公告列表 DTO | 1 小时 + 0～10 分钟随机偏移 | 公告新增、修改、删除提交后删除 |
| `mindharbor:cache:sensitive-word:enabled` | 启用中的敏感词 | 20 分钟 + 0～5 分钟随机偏移 | 敏感词新增、修改、删除提交后删除 |
| `mindharbor:cache:sensitive-word:all` | 完整敏感词词库 | 20 分钟 + 0～5 分钟随机偏移 | 敏感词新增、修改、删除提交后删除 |

- 缓存只保存专用的 `*CacheDTO`，不直接缓存 MyBatis 实体，实体字段变化不会让旧缓存无法反序列化。
- 关键词搜索不缓存，避免为每种组合生成缓存键。
- 空缓存只用于文章详情，列表不写空缓存。
- TTL 带随机偏移，避免大量键在同一时刻集中失效造成缓存雪崩。

### 单飞锁与多实例限制

- 同一 JVM 内按缓存键隔离单飞锁：同一键并发未命中时，只有拿到锁的请求回源，其余请求等待后重读缓存。
- 等待上限由 `cache.rebuild-lock.wait-millis` 控制（默认 100ms）。等待超时不会无限阻塞，而是重读缓存，仍为空时直接回源，可用性优先。
- 本地锁没有租约和自动过期机制，因此配置中不提供 `lease-seconds` 之类的字段。
- 本地锁只在当前 JVM 内生效。改为多实例部署后，不同实例仍可能同时回源，需要替换为经过验证的 Redis 分布式锁
  （例如 Redisson），并处理锁过期与续租；本阶段不实现分布式锁，也不手写 Redis 锁。
- Redis 是可丢失组件：服务在 Redis 不可用时仍能正常读写业务数据，只是暂时失去缓存收益。

### 缓存参数

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `CACHE_ARTICLE_TTL_SECONDS` | `3600` | 文章列表与详情缓存基准 TTL |
| `CACHE_ARTICLE_JITTER_SECONDS` | `600` | 文章缓存 TTL 随机偏移上限 |
| `CACHE_ARTICLE_MISSING_TTL_SECONDS` | `300` | 文章空缓存 TTL |
| `CACHE_ARTICLE_MISSING_JITTER_SECONDS` | `60` | 文章空缓存 TTL 随机偏移上限 |
| `CACHE_ANNOUNCEMENT_TTL_SECONDS` | `3600` | 公告列表缓存基准 TTL |
| `CACHE_ANNOUNCEMENT_JITTER_SECONDS` | `600` | 公告缓存 TTL 随机偏移上限 |
| `CACHE_SENSITIVE_WORD_TTL_SECONDS` | `1200` | 敏感词列表缓存基准 TTL |
| `CACHE_SENSITIVE_WORD_JITTER_SECONDS` | `300` | 敏感词缓存 TTL 随机偏移上限 |
| `CACHE_REBUILD_LOCK_WAIT_MILLIS` | `100` | 单飞锁等待上限；调大能让更多并发请求复用回源结果，但会增加等待延迟 |

### 启动与验证

1. 启动 MySQL 并确认库与账号可用。
2. 启动 Redis。跳过这一步也能启动服务，缓存会自动降级到 MySQL。
3. 按需启动 Chroma 与模型服务，再启动后端。
4. 验证缓存：登录后连续打开两次科普文章列表，在 Redis 中执行 `KEYS mindharbor:cache:*` 应能看到已回填的键。
5. 验证降级：停掉 Redis，页面仍能正常显示内容（此时走 MySQL），同时后端日志只出现缓存读写失败的 WARN，不影响业务。
6. 验证失效：修改一篇文章后，对应的列表键与详情键应当消失，下一次查询重新回填。

### Docker Compose 部署

`assistant-backend/docker-compose.yml` 描述单机容器化部署，共五个服务：MySQL、Redis、Chroma、backend、nginx。
`backend/app.jar` 与 `web/dist` 以 bind mount 挂载，不自建镜像；宿主机只需 Docker 与 Compose 插件，
不需要在宿主机安装 JDK、MySQL 或 Redis。只有 nginx 映射宿主机端口，其余服务只在 compose 内网互通。

部署目录约定（下文以 `/opt/mindharbor` 为例，compose 内 `./` 相对路径均以 compose 文件所在目录为基准）：

| 路径 | 来源 |
| --- | --- |
| `docker-compose.yml` | 仓库 `assistant-backend/docker-compose.yml` |
| `.env` | 由 `assistant-backend/.env.example` 复制填写，**不提交版本库** |
| `nginx.conf` | 仓库 `assistant-web/nginx.conf` |
| `backend/app.jar` | 后端 `mvn -DskipTests package` 产物 |
| `web/dist/` | 前端 `npm run build` 产物 |
| `db/` | 仓库 `assistant-backend/src/main/resources/db/`（`schema.sql` 与 `migrations/`） |
| `data/mysql`、`data/redis`、`data/chroma`、`uploads/` | 运行时生成，**属于必须备份的数据目录** |

初始化顺序：

1. `docker compose up -d mysql redis`，等 `docker compose ps` 中 `mh-mysql` 显示 `healthy`。
2. 导入基线脚本（在宿主机执行，密码由容器内环境变量提供，不写在命令行里）：

   ```bash
   docker exec -i mh-mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD"' < db/schema.sql
   ```

   `schema.sql` 是完整基线，含全部表与全部列，**全新库只导它即可**；`db/migrations/` 只用于给已存在的旧库做增量升级。
3. `docker compose up -d` 启动其余服务，`docker compose logs backend` 出现 `Started AssistantApplication in … seconds` 即为就绪。

#### Chroma 向量库首次引导（新环境必做）

`spring.ai.vectorstore.chroma.initialize-schema: true` 在当前依赖版本下**不会真正生效**：
`ChromaVectorStore.afterPropertiesSet` 依赖 `getCollection` 返回 `null` 来判断"集合不存在"，
而 `ChromaApi.getCollection` 在集合缺失时直接抛异常，导致其后建租户、建库、建集合的代码无法到达。
结果是知识库文档的向量化与检索都会以 404 `Collection […] does not exist` 失败，
而后端对向量库故障一律静默降级，因此**每个新环境都必须手工引导一次**。
三条命令顺序不可颠倒（库挂在租户下、集合挂在库下）：

```bash
docker compose exec -T nginx wget -qO- --header="Content-Type: application/json" \
  --post-data='{"name":"SpringAiTenant"}' "http://chroma:8000/api/v2/tenants"; echo

docker compose exec -T nginx wget -qO- --header="Content-Type: application/json" \
  --post-data='{"name":"SpringAiDatabase"}' "http://chroma:8000/api/v2/tenants/SpringAiTenant/databases"; echo

docker compose exec -T nginx wget -qO- --header="Content-Type: application/json" \
  --post-data='{"name":"mental-health-knowledge","metadata":{"hnsw:space":"cosine"}}' \
  "http://chroma:8000/api/v2/tenants/SpringAiTenant/databases/SpringAiDatabase/collections"; echo
```

- Spring AI 使用自己的租户与库（`SpringAiTenant` / `SpringAiDatabase`），**不是** Chroma 原生的
  `default_tenant` / `default_database`；建错位置的表现是报错文案一字不变，仍是 `Collection does not exist`。
- 租户与库成功时返回 `{}`，集合成功时返回带 `id` 的 JSON；`409` 表示对象已存在，继续下一步即可；
  `400` 通常表示请求体 JSON 语法有误；`404` 表示租户或库不存在。
- ⚠️ 集合**必须带 `"metadata":{"hnsw:space":"cosine"}`**。漏掉会退化成 Chroma 默认的 `l2` 空间，
  而检索按 `score = 1 - distance ≥ 0` 过滤，`l2` 下该条件等价于余弦相似度 ≥ 0.5，会把措辞不同的命中整片滤掉 ——
  表现为"向量化成功但知识库不起作用"，前后端都不报错、也不打日志。
- 集合名要与 `spring.ai.vectorstore.chroma.collection-name` 一致（默认 `mental-health-knowledge`）。

验证（全部走容器内网，不依赖宿主机工具）：

```bash
# 1. 容器是否在提供服务
docker compose exec -T nginx wget -qO- http://chroma:8000/api/v2/heartbeat; echo

# 2. 集合是否还在（返回带 id 的 JSON）
docker compose exec -T nginx wget -qO- \
  "http://chroma:8000/api/v2/tenants/SpringAiTenant/databases/SpringAiDatabase/collections"; echo

# 3. 集合里有多少条向量。计数路由按集合 id 而非集合名；count 数的是分片，不是文档数
ID=$(docker compose exec -T nginx wget -qO- \
  "http://chroma:8000/api/v2/tenants/SpringAiTenant/databases/SpringAiDatabase/collections" \
  | tr -d '\r' | grep -oE '"id":"[^"]+"' | head -1 | cut -d'"' -f4)
docker compose exec -T nginx wget -qO- \
  "http://chroma:8000/api/v2/tenants/SpringAiTenant/databases/SpringAiDatabase/collections/$ID/count"; echo
```

Chroma 的数据只存在于 bind mount 的 `data/chroma`，`restart`、`down`、删除容器重建都不会丢；
但**删掉该目录或更换机器等于要重跑上面的引导**。集合的向量实体存放在以集合 id 命名的索引目录里，
`chroma.sqlite3` 只记录"有哪些集合"，因此**不要用手工删除目录的方式删除集合**
（会留下"名字还在、数据没了"的半坏状态）；要删必须走
`DELETE /api/v2/tenants/{tenant}/databases/{database}/collections/{collection_name}`，删完目录由 Chroma 自行清理。

#### 镜像版本与中文字体

- `chromadb/chroma` 必须钉具体版本（当前为 `1.0.0`），**不要使用 `latest`**：spring-ai 1.0.0 只兼容 Chroma 1.x，
  拉到 2.x 会因 API 不兼容直接连不上，且新版可能迁移 `chroma.sqlite3` 结构而无法回退。
  换版本前先用 `docker tag` 在本地为当前镜像建版本别名，确认远端存在同名 tag 再改 compose，
  避免镜像被清理后新容器起不来。
- 会话导出 PDF 的中文字体是三级兜底（`EXPORT_PDF_FONT_PATH` → jar 内 `fonts/` → 系统字体）。
  Ubuntu 默认不安装 CJK 字体，需要在**宿主机**安装一次：
  `apt-get install -y --no-install-recommends fonts-noto-cjk`。
  compose 已将宿主机 `/usr/share/fonts` 以目录级 bind mount 挂进容器，装完**无需重启 backend** 即可生效
  （字体解析没有负缓存，上一次失败的解析结果不会被记住）。三级全部落空时导出接口返回 400 与"未配置中文字体"。

### 多实例迁移要求

单实例部署时本地单飞锁足够使用。扩到多实例前必须完成：

1. 把本地单飞锁替换为 Redis 分布式锁，覆盖文章列表、文章详情、公告列表与敏感词列表的键。
2. 分布式锁要有过期与续租策略，避免业务未执行完锁就过期。
3. 缓存失效逻辑保持不变：仍然是事务提交后删除，仍然靠 TTL 兜底。
4. 复核 Redis 容量与淘汰策略，确保缓存键不会被过早淘汰而放大数据库压力。

## 访问地址

- 服务地址为 `http://localhost:8080`，`server.servlet.context-path` 已配置为 `/api/v1`，业务接口真实地址形如 `http://localhost:8080/api/v1/chat/sessions`。
- `openapi.yaml` 是接口的唯一权威定义。导入 Apifox 或 Postman 时填写服务域名 `http://localhost:8080`，并保留 YAML 中的 `/api/v1` 前缀。
- `pom.xml` 已声明 `springdoc-openapi-starter-webmvc-ui`（版本由属性 `springdoc.version` 管理），首次编译会自动下载依赖；依赖就绪后可在浏览器打开 `http://localhost:8080/api/v1/swagger-ui/index.html` 调试接口（`/api/v1/swagger-ui.html` 会跳转到该地址），原始文档 JSON 位于 `http://localhost:8080/api/v1/v3/api-docs`。前端开发服务器只代理 `/api`，因此把域名换成 `http://localhost:5173` 访问同一路径同样可用。`openapi.yaml` 仍是接口的唯一权威定义，在线页面只作辅助调试。
- 注意 `/doc.html` 是 Knife4j 的页面路径，本项目使用 springdoc，该地址不存在（在前端开发服务器上访问会落到 SPA 首页）。
- `/swagger-ui.html`、`/swagger-ui/**`、`/v3/api-docs/**` 已同时加入 Spring Security 白名单和 JWT 拦截器排除列表，无需登录即可访问。
