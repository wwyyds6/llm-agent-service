# LLM Agent Service

> 基于 SpringBoot + 大模型 Function Calling 的多工具智能 Agent 服务，支持 SSE 流式输出、多轮对话持久化与工具调用可视化。

用户用自然语言提问，服务自动判断意图、调用后端工具（获取时间 / 数学计算）获取真实数据，再生成回答。全流程流式输出，对话记录落库，支持多会话管理。

---

## 为什么做这个项目

大模型本身有两个硬伤：**它不知道当前时间**，而且**不擅长精确计算**（大数乘法经常算错）。

通过 **Function Calling**，让模型在需要时调用真实的后端函数，可以把"模型编造"变成"工具提供真实数据"。这是 LLM 应用落地最核心的工程手段。

但在实际实现中会遇到几个真实问题，本项目都做了处理（见下方「遇到并解决的问题」）：

- 流式模式下 `tool_calls` 是分片到达的，必须按 `index` 拼接
- 模型输出的换行符会破坏 SSE 协议帧结构
- 历史对话中的**时效性信息**（如时间）会污染后续判断，导致模型复用过期数据

---

## 功能特性

**对话能力**

- **SSE 流式输出**：基于 `SseEmitter` 逐字推送，首字响应快，无需等待全文生成
- **多轮对话记忆**：服务端从数据库读取最近 20 条历史组装上下文，刷新/重开页面不丢失
- **多会话管理**：会话列表、切换、重命名、级联删除
- **上下文裁剪**：服务端限制带入历史条数，控制 token 消耗

**Agent 能力**

- **Function Calling**：模型自主判断是否需要调用工具，支持多轮连续调用
- **流式与工具调用融合**：正文边收边推，工具调用事件独立推送，前端可视化展示
- **工具执行与结果回灌**：工具执行结果以 `role=tool` 回灌模型，由模型生成最终回答
- **异常降级**：工具执行失败时把错误信息返回给模型，由模型自行纠正而非直接报错
- **防死循环**：Agent 循环最多 5 轮

**工程规范**

- **统一响应体** `Result<T>`：前后端契约固定，前端统一处理 `code != 200`
- **全局异常处理** `@RestControllerAdvice`：参数校验异常 / 业务异常 / 系统异常分层处理；系统异常记录完整堆栈但**不暴露给前端**
- **参数校验**：`@Valid` + `@NotBlank` / `@Size`，校验信息经全局异常处理器统一返回
- **分层架构**：Controller / Service / Mapper / Entity / DTO 清晰分层
- **密钥安全管理**：敏感配置放本地未提交文件，仓库提供 `application-local-example.yml` 模板

---

## 技术栈

| 类别 | 技术 |
| --- | --- |
| 语言 | Java 21 |
| 框架 | Spring Boot 4.0.8 |
| 大模型 | DeepSeek API（OpenAI 兼容协议） |
| 流式输出 | SSE（`SseEmitter` + 前端 `fetch` + `ReadableStream`） |
| 数据库 | MySQL 8.4 + MyBatis 4.0.1 |
| ORM | MyBatis（注解方式） |
| HTTP 客户端 | `RestTemplate` |
| JSON 解析 | Jackson 3 |
| 表达式计算 | Spring Expression Language (SpEL) |
| 参数校验 | Jakarta Validation (Hibernate Validator) |
| 构建 | Maven |
| 前端 | 原生 HTML / CSS / JavaScript（无框架） |

---

## Agent 工作流程

```
用户："现在几点了？"
  │
  ├─① 后端 → DeepSeek：{ messages: [...], tools: [getCurrentTime, calculate] }
  │
  ├─② DeepSeek → 后端：流式返回 tool_calls 分片
  │      data: {"delta":{"tool_calls":[{"index":0,"id":"call_0","function":{"name":"getCurrentTime","arguments":""}}]}}
  │      data: {"delta":{"tool_calls":[{"index":0,"function":{"arguments":"{}"}}]}}
  │
  ├─③ 后端：按 index 拼接分片 → 执行 getCurrentTime() → "2026年10月10日 11:50:18"
  │      同时写入 tool_call_log 表，并通过 SSE 的 tool 事件推给前端
  │
  ├─④ 后端 → DeepSeek：{ messages: [..., { role: "tool", tool_call_id: "call_0", content: "..." }] }
  │
  └─⑤ DeepSeek → 后端：流式返回最终回答 → 逐字推给前端
```

**关键点**：模型本身不执行代码，它只返回"要调用哪个函数、参数是什么"；真正执行的是后端 Java 代码。

---

## 流式 + 工具调用融合（技术难点）

**问题**：普通模式下模型一次性返回完整的 `tool_calls`，直接读即可。但流式模式下 `tool_calls` 是**分片到达**的：

```
data: {"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call_0","function":{"name":"getCurrentTime","arguments":""}}]}}]}
data: {"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":"{}"}}]}}]}
data: {"choices":[{"delta":{},"finish_reason":"tool_calls"}]}
```

`id` 只在第一片出现，`arguments` 需要字符串累加。

**解法**：按 `index` 建三个 Map（`id` / `name` / `arguments`）累积分片，流结束后拼成完整的工具调用：

```java
Map<Integer, String> ids = new LinkedHashMap<>();
Map<Integer, String> names = new LinkedHashMap<>();
Map<Integer, StringBuilder> args = new LinkedHashMap<>();

streamOnce(messages, onDelta, (index, id, name, argDelta) -> {
    if (id != null && !id.isEmpty()) ids.putIfAbsent(index, id);
    if (name != null && !name.isEmpty()) names.putIfAbsent(index, name);
    args.computeIfAbsent(index, k -> new StringBuilder())
        .append(argDelta == null ? "" : argDelta);
});
```

**另一个坑**：SSE 是**按换行分帧**的，而模型输出可能包含换行符。如果把正文直接塞进 `data:`，换行会把协议帧打断，前端丢内容。

**解法**：所有 SSE 数据统一包成 JSON（`{"text":"..."}`），JSON 会把换行转义成 `\n`，永远不会破坏帧结构。

---

## 数据库表设计

```sql
-- 会话表
CREATE TABLE conversation (
  id         BIGINT       NOT NULL AUTO_INCREMENT,
  title      VARCHAR(100) NOT NULL DEFAULT '新会话',
  created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_updated_at (updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 消息表
CREATE TABLE message (
  id              BIGINT      NOT NULL AUTO_INCREMENT,
  conversation_id BIGINT      NOT NULL,
  role            VARCHAR(20) NOT NULL COMMENT 'user / assistant',
  content         TEXT        NOT NULL,
  created_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_conversation (conversation_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 工具调用日志表
CREATE TABLE tool_call_log (
  id         BIGINT      NOT NULL AUTO_INCREMENT,
  message_id BIGINT      NULL,
  tool_name  VARCHAR(50) NOT NULL,
  arguments  TEXT        NULL,
  result     TEXT        NULL,
  cost_ms    INT         NOT NULL DEFAULT 0,
  success    TINYINT(1)  NOT NULL DEFAULT 1,
  created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_tool_name (tool_name),
  KEY idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

**设计说明**：

| 设计点 | 原因 |
| --- | --- |
| `utf8mb4` | MySQL 的 `utf8` 是残缺实现（最多 3 字节），**存不了 emoji**；`utf8mb4` 才是完整 UTF-8 |
| `content` 用 `TEXT` | 消息长度不可控，TEXT 最大 64KB 且不占用行内空间 |
| 联合索引 `(conversation_id, id)` | 查某会话消息时按 `conversation_id` 过滤、按 `id` 排序，**联合索引最左前缀命中且 id 有序，可直接用索引完成排序，避免 filesort** |
| `created_at` 默认 `CURRENT_TIMESTAMP` | 时间戳由数据库生成，避免应用服务器时区不一致 |
| `ON UPDATE CURRENT_TIMESTAMP` | 更新时间自动维护，无需应用层手动更新 |
| `tool_call_log` 记录 `cost_ms` / `success` | 提供可观测性：可统计工具调用次数、平均耗时、失败率 |

---

## 快速开始

### 1. 环境要求

- JDK 21+
- Maven 3.8+
- MySQL 8.0+
- 一个 DeepSeek API Key（[platform.deepseek.com](https://platform.deepseek.com)）

### 2. 建库建表

执行上面的 SQL（或执行 `docs/schema.sql`）。

### 3. 配置

复制 `src/main/resources/application-local-example.yml` 为 `application-local.yml`，填入配置：

```yaml
deepseek:
  api-key: sk-在此填入你的密钥
  base-url: https://api.deepseek.com
  model: deepseek-chat

spring:
  datasource:
    url: jdbc:mysql://localhost:3306/llm_agent?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false
    username: root
    password: 你的数据库密码
    driver-class-name: com.mysql.cj.jdbc.Driver
```

> ⚠️ `application-local.yml` 已被 `.gitignore` 排除，**不会提交到仓库**。请勿把真实密钥写入 `application.yaml`。

### 4. 启动

```bash
mvn spring-boot:run
```

访问 [http://localhost:8080/index.html](http://localhost:8080/index.html) 打开对话页面。

---

## 接口说明

### 对话与会话

| 接口 | 方法 | 说明 |
| --- | --- | --- |
| `/agent/stream?conversationId={id}` | POST | **Agent 流式对话**（主接口），body 为纯文本消息，返回 SSE |
| `/chat/stream?conversationId={id}` | POST | 纯流式对话（不启用工具），用于对比调试 |
| `/conversation` | GET | 会话列表（按更新时间倒序） |
| `/conversation` | POST | 新建会话 |
| `/conversation/{id}/messages` | GET | 某会话的全部消息 |
| `/conversation/{id}/title` | PUT | 重命名会话（`{"title":"..."}`，参数校验：非空且 ≤50 字） |
| `/conversation/{id}` | DELETE | 删除会话及其消息 |
| `/ping` | GET | 健康检查 |
| `/test-llm` | GET | 非流式调用大模型（调试用） |
| `/agent/chat?msg={问题}` | GET | 非流式 Agent 对话（调试用） |

> **注意**：流式接口（`/agent/stream`、`/chat/stream`）**不返回统一响应体 `Result`** —— 它们遵循 SSE 协议逐帧推送数据，包装成 `Result` 会破坏流式语义。普通 CRUD 接口统一返回 `Result<T>`。

### SSE 事件格式

`/agent/stream` 推送两类命名事件，数据均为 JSON：

| 事件名 | 数据示例 | 含义 |
| --- | --- | --- |
| `delta` | `{"text":"你好"}` | 正文分片，前端追加显示 |
| `tool` | `{"name":"getCurrentTime","arguments":"{}","result":"...","costMs":1,"success":true}` | 工具调用通知，前端展示工具标签 |

### 工具列表

| 工具名 | 功能 | 参数 |
| --- | --- | --- |
| `getCurrentTime` | 获取当前日期时间 | 无 |
| `calculate` | 计算数学表达式（SpEL） | `expression`：表达式字符串 |

---

## 实测数据

> **测试环境**：本地开发机（MySQL 8.4 + JDK 25），模型 `deepseek-flash`（`deepseek-chat` 当前指向）
> **测试方法**：11 条用例（5 条时间类 + 5 条计算类 + 1 条普通对话），同会话连续提问
> **注**：同会话测试会受到上下文影响；逐条新建会话的重新测量正在进行中，数据会更新

| 指标 | 结果 |
| --- | --- |
| 工具调用命中率 | **27%**（3/11）—— 简单算术模型倾向自行计算，未触发工具 |
| 回答正确率 | **91%**（10/11） |
| `getCurrentTime` 平均耗时 | 0–1 ms |
| `calculate` 平均耗时 | 41–57 ms |
| 唯一失败用例 | "现在几点了"（同会话重复提问，模型复用了历史中的过期时间） |

**关于两个指标的区别**：

- **工具调用命中率**：模型主动调用工具的比例。偏低是正常现象 —— 简单算术模型自己算也能算对。
- **回答正确率**：最终答案是否正确。这才是用户关心的指标。

---

## 项目结构

```
src/main/java/com/wangqihui/llmagent/
├── LlmAgentServiceApplication.java   # 启动类
├── AppConfig.java                    # Bean 配置（RestTemplate / ObjectMapper）
│
├── common/                           # 通用组件
│   ├── Result.java                   # 统一响应体
│   ├── BusinessException.java        # 自定义业务异常
│   └── GlobalExceptionHandler.java   # 全局异常处理
│
├── dto/
│   └── RenameRequest.java            # 请求 DTO（含参数校验注解）
│
├── entity/                           # 数据库实体
│   ├── Conversation.java
│   ├── Message.java
│   └── ToolCallLog.java
│
├── mapper/                           # MyBatis Mapper
│   ├── ConversationMapper.java
│   ├── MessageMapper.java
│   └── ToolCallLogMapper.java
│
├── LlmService.java                   # 大模型调用（非流式 + 流式）
├── AgentService.java                 # Agent 循环与工具编排（核心）
├── ToolService.java                  # 工具的具体实现与分发
│
├── PingController.java               # 健康检查
├── TestController.java               # 调试接口
├── ConversationController.java       # 会话管理接口
├── StreamController.java             # 流式对话接口（SSE + Agent）
└── AgentController.java              # Agent 非流式接口

src/main/resources/
├── application.yaml                  # 主配置（可提交）
├── application-local-example.yml     # 配置模板（密钥占位）
└── static/index.html                 # 前端页面
```

---

## 遇到并解决的问题

**1. Spring Boot 4 包名变更导致配置失效**

从 Spring Boot 3 升级到 4 后，自动配置类的包路径由 `org.springframework.boot.autoconfigure.jdbc.*` 改为 `org.springframework.boot.jdbc.autoconfigure.*`。沿用旧路径的 `@SpringBootApplication(exclude = ...)` 会**静默失效**，表现为"配置写了但没生效"。

**2. 流式 `tool_calls` 分片拼接**

见上方「流式 + 工具调用融合」章节。

**3. SSE 帧结构被换行符破坏**

见上方「流式 + 工具调用融合」章节。

**4. 上下文污染：模型复用历史中的过期时间**

**现象**：问"现在几点了"，模型调用工具并回答；几分钟后再问同一问题，模型看到历史里已有时间信息，**直接复用旧答案且不再调用工具**，导致回答过期。

**定位**：`tool` 角色的消息并未落库，进历史的是**模型自己转述的答案**（含时间），下一轮它认为"我已经知道答案了"。

**解法**：在系统提示词中明确声明"历史对话中出现过的时间信息均已失效，回答当前时间相关问题必须重新调用工具"。修复后验证：同一会话间隔 6 分钟两次提问，均重新调用了工具。

**5. API 密钥泄露风险处理**

开发初期误将含真实密钥的 `application-local.yml` 加入了暂存区。通过 `git rm --cached` 撤销追踪 + 补充 `.gitignore` 规则解决，并建立规范：**真实配置不提交，仅提交 `application-local-example.yml` 模板**。

---

## 已知问题与改进方向

| 问题 | 现状 | 改进方向 |
| --- | --- | --- |
| 时效性数据可能被历史污染 | 已通过系统提示词缓解 | 检测到时间类意图时用 `tool_choice` **强制**调用工具（两级策略：模型自主决策 + 关键场景兜底） |
| 简单算术不触发工具 | 模型自行计算，多数正确但不稳定 | 同上 |
| 会话「懒创建」 | 点击新建即落库，会产生空会话 | 首次发消息时才落库 |
| 上下文无限增长 | 仅保留最近 20 条（截断） | 对早期对话做摘要压缩，或引入向量检索做长期记忆 |
| 接口限流 | 未实现 | Redis 计数器限流，防刷 |
| 单元测试 | 未覆盖 | 补充 JUnit + Mockito 测试工具函数与 Agent 循环 |

---

## 后续计划

- [ ] Redis 缓存会话上下文，减少数据库查询
- [ ] 新增工具：数据库查询、联网检索
- [ ] 意图路由 + `tool_choice` 强制调用，提升时效性场景可靠性
- [ ] 接口限流与单测覆盖
- [ ] Docker 化部署

---

## License

MIT
