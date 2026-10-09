# LLM Agent Service

> 基于 SpringBoot + 大模型 Function Calling 的多工具智能 Agent 服务

用户用自然语言提问，服务自动判断意图、调用后端工具（获取时间 / 数学计算 / 数据库查询）获取真实数据，再生成回答。全流程流式输出，具备多轮对话记忆能力。

## 为什么做这个项目

大模型本身有局限：**它不知道当前时间，也不擅长精确计算**（大数乘法经常算错）。

通过 **Function Calling**，让模型在需要时调用真实的后端函数，可以把"模型编造"变成"工具提供真实数据"。这是 LLM 应用落地最核心的工程手段。

## 功能特性

- **流式对话**：基于 SSE（`SseEmitter`）实现逐字输出，首字响应快，无需等待全文生成
- **多轮对话记忆**：保留最近 20 轮上下文，模型能记住前文信息
- **Function Calling**：模型自主判断是否需要调用工具，可多轮连续调用
- **工具执行与结果回灌**：工具执行结果以 `role=tool` 回灌模型，由模型生成最终回答
- **异常降级**：工具执行失败时返回错误信息给模型，由模型自行纠正而非直接报错
- **防死循环**：Agent 循环最多 5 轮，避免模型无限调用工具
- **前端页面**：原生 HTML/CSS/JS 实现，基于 `fetch` + `ReadableStream` 解析 SSE 流

## 技术栈

| 类别 | 技术 |
| --- | --- |
| 语言 | Java 21 |
| 框架 | Spring Boot 4.0.8 |
| 大模型 | DeepSeek API（OpenAI 兼容协议） |
| 流式输出 | SSE（`SseEmitter`） |
| HTTP 客户端 | `RestTemplate` |
| JSON 解析 | Jackson 3 |
| 表达式计算 | Spring Expression Language (SpEL) |
| 构建 | Maven |
| 前端 | 原生 HTML / CSS / JavaScript |

## Agent 工作流程

```
用户："现在几点了？"
  │
  ├─① 后端 → DeepSeek：{ messages: [...], tools: [getCurrentTime, calculate] }
  │
  ├─② DeepSeek → 后端：{ finish_reason: "tool_calls",
  │                      tool_calls: [{ function: { name: "getCurrentTime" } }] }
  │
  ├─③ 后端：执行 getCurrentTime() → "2026年10月09日 20:30:06"
  │
  ├─④ 后端 → DeepSeek：{ messages: [..., { role: "tool", content: "2026年10月09日 20:30:06" }] }
  │
  └─⑤ DeepSeek → 后端：{ content: "现在是 2026年10月9日 20:30" } → 返回给前端
```

**关键点**：模型本身不执行代码，它只返回"要调用哪个函数、参数是什么"；真正执行的是后端 Java 代码。

## 快速开始

### 1. 环境要求

- JDK 21+
- Maven 3.8+
- 一个 DeepSeek API Key（[platform.deepseek.com](https://platform.deepseek.com)）

### 2. 配置 API Key

复制 `src/main/resources/application-local-example.yml` 为 `application-local.yml`，填入你自己的密钥：

```yaml
deepseek:
  api-key: sk-在此填入你的密钥
  base-url: https://api.deepseek.com
  model: deepseek-chat
```

> ⚠️ `application-local.yml` 已被 `.gitignore` 排除，**不会提交到仓库**。请勿把真实密钥写入 `application.yaml`。

### 3. 启动

```bash
mvn spring-boot:run
```

访问 [http://localhost:8080/index.html](http://localhost:8080/index.html) 打开对话页面。

## 接口说明

| 接口 | 方法 | 说明 |
| --- | --- | --- |
| `/ping` | GET | 健康检查 |
| `/test-llm` | GET | 非流式调用大模型（调试用） |
| `/chat/stream` | POST | 流式对话，请求体为消息历史 JSON 数组 |
| `/agent/chat` | GET | 带工具调用的 Agent 对话，`?msg=你的问题` |

### 工具列表

| 工具名 | 功能 | 参数 |
| --- | --- | --- |
| `getCurrentTime` | 获取当前日期时间 | 无 |
| `calculate` | 计算数学表达式 | `expression`：表达式字符串 |

## 实测数据

> 以下为本人实测数据，测试环境：本地开发机，模型 `deepseek-flash`（`deepseek-chat` 的当前指向）

| 指标 | 结果 |
| --- | --- |
| 工具调用准确率 | 【待补充：20 条测试用例】 |
| 典型响应耗时（简单问题） | 【待补充】 |
| 数学计算准确率 | 100%（借助工具，模型自算易出错） |
| 单次对话平均 token 消耗 | 【待补充】 |

## 项目结构

```
src/main/java/com/wangqihui/llmagent/
├── LlmAgentServiceApplication.java   # 启动类
├── AppConfig.java                    # Bean 配置（RestTemplate / ObjectMapper）
├── LlmService.java                   # 大模型调用（非流式 + 流式）
├── AgentService.java                 # Agent 循环与工具编排（核心）
├── ToolService.java                  # 工具的具体实现与分发
├── PingController.java               # 健康检查
├── TestController.java               # 调试接口
├── StreamController.java             # 流式对话接口（SSE）
└── AgentController.java              # Agent 对话接口

src/main/resources/
├── application.yaml                  # 主配置
├── application-local-example.yml     # 配置模板（密钥占位）
└── static/index.html                 # 前端对话页面
```

## 后续计划

- [ ] 对话记录持久化（MySQL）：`conversation` / `message` / `tool_call_log` 三张表
- [ ] 工具调用日志可视化（前端展示调用了哪些工具、耗时）
- [ ] 接入 Redis 缓存会话上下文
- [ ] 支持更多工具（数据库查询、联网检索）
- [ ] 接口限流与统一异常处理

## License

MIT