# Spring AI MCP 示例（JDK 21）

这是一个 **以 MCP 为核心的 Spring AI 服务示例**，基于 **Spring AI 2.0.0-M2**，适合用来学习 Spring AI 与 Model Context Protocol（MCP）的集成。  
- **事件管理**：多组 MCP 工具（创建/更新/统计/调度/关联/Runbook 推荐）。  
- **往表中插入数据**：`data_record` 表（**MySQL**）+ MCP 工具 `insertData` / `listData`，支持 MCP 客户端或 **DeepSeek 大模型**通过自然语言“帮我在表里插一条数据”调用。

---

## 0. MCP 小白入门：MCP 是什么？能干什么？

如果你是第一次接触 MCP，先看这一节，用最直白的话说清楚 **MCP 是什么** 以及 **在你这个项目里具体干什么**。

### 0.1 一句话理解 MCP

**MCP = Model Context Protocol（模型上下文协议）**，是一套**约定**：  
让 **AI（大模型）** 和 **你的后端服务** 用同一种“语言”对话——AI 可以**发现你提供了哪些能力**，并**按约定格式调用**这些能力。

- **没有 MCP**：AI 只能聊天，不能操作你的系统（查事件、创建工单、推荐 Runbook 等）。
- **有了 MCP**：AI 知道你有“创建事件”“查事件列表”“推荐运行手册”等**工具**，并能按名字和参数去**调用**它们，把结果再用来回答用户。

所以：**MCP 的作用 = 让 AI 能安全、规范地调用你暴露的“工具”（接口）**。

### 0.2 打个比方

- **你的服务**：像一家餐厅，提供很多“菜”（工具）：创建事件、查事件、推荐 Runbook……
- **MCP 协议**：像**菜单 + 点菜规则**：
  - 菜单 = 你对外说“我有哪些工具、每个工具要什么参数”（即 **tools/list**）。
  - 点菜 = 对方按规则说“我要调用 createIncident，参数是 title=xxx, severity=高”（即 **tools/call**）。
- **AI（或 Cursor/Claude 等）**：像顾客，先看菜单（list tools），再点菜（call tool），拿到结果后继续对话或再点下一道菜。

MCP 就是这份“菜单 + 点菜规则”的**统一标准**，大家按这个标准来，AI 和你的服务就能无缝配合。

### 0.3 三个角色（本项目中）

| 角色 | 是谁 | 做什么 |
|------|------|--------|
| **MCP 服务端（Server）** | 本项目：`spring-ai-mcp-demo`，跑在 8090 端口 | 提供“菜单”（工具列表）和“做菜”（执行工具）。本项目中就是：创建/查询/更新事件、Runbook 推荐、跟进调度等。 |
| **MCP 客户端（Client）** | Cursor、Claude Desktop、Postman、或其它支持 MCP 的 AI 应用 | 先向服务端要“菜单”（list tools），再发“点菜单”（call tool），把结果交给用户或 AI。 |
| **大模型（LLM）** | 在 Cursor/Claude 里用的 AI | 根据用户问题，决定“该调用哪个工具、参数填什么”，然后由 MCP 客户端去调用你的服务。 |

也就是说：**你的项目 = MCP 服务端**；**Cursor/Postman 等 = MCP 客户端**；**AI 根据对话决定调用哪个工具**。

### 0.4 一次完整流程（以“帮我创建一条事件”为例）

1. 用户在 Cursor 里说：“帮我创建一条高优先级事件，标题是 API 延迟飙升。”
2. Cursor（MCP 客户端）先问你本项目的 MCP 服务：“你有哪些工具？” → 服务返回：有 `createIncident`、`listIncidents`、`recommendRunbookForIncident` 等。
3. Cursor 背后的 AI 看到有 `createIncident`，根据用户话填参数：`title=API 延迟飙升`、`severity=高` 等。
4. Cursor 按 MCP 协议向 `http://localhost:8090/mcp` 发 **tools/call**：`name=createIncident`，`arguments={...}`。
5. 你的项目执行 `IncidentToolService.createIncident(...)`，把事件存进内存，返回结果（例如新事件的 ID）。
6. Cursor 把结果给用户看：“已创建事件 INC-0001-xxx。”

整个过程里，**MCP 只负责“怎么说、怎么传”**（协议）；**“做什么”** 由你写的 Java 代码（如 `IncidentToolService`）决定。

### 0.5 本项目中 MCP 具体做了什么？

- **暴露一个 HTTP 入口**：`POST http://localhost:8090/mcp`（我们配置成 STATELESS，无需会话，方便 Postman 测试）。
- **约定通信格式**：JSON-RPC 2.0，例如：
  - 方法名：`initialize`（握手）、`tools/list`（列工具）、`tools/call`（调工具）。
  - 参数：如 `name=createIncident`、`arguments={ title, severity, ... }`。
- **把“工具”注册到 MCP**：你在 `IncidentToolService`、`RunbookToolService`、`WorkflowToolService` 里用 `@Tool` 写的方法，会通过 `McpToolsConfig` 里的 `ToolCallbackProvider` 注册成 MCP 的“工具”；客户端 `tools/list` 时能看到，`tools/call` 时就会调到这些方法。

所以：**MCP 在本项目里的作用 = 把“创建事件、查事件、推荐 Runbook”等能力，用统一协议暴露给 AI/客户端调用。**

### 0.6 和普通 HTTP API 的区别

- **普通 REST API**：你定 URL（如 `/api/incidents`）、定请求体；客户端要自己知道每个接口的路径和参数。
- **MCP**：  
  - 客户端**先问**“你有什么工具”（tools/list），拿到**工具名 + 参数 schema**；  
  - 再**统一用** `tools/call` 一个入口，传 `name` + `arguments`。  
这样 AI 可以**自动发现**你能做什么、需要什么参数，而不必写死一堆 API 文档。  
总结：**MCP 是给“AI 调用你的能力”用的标准协议；普通 API 是给人或固定脚本调的接口。**

### 0.7 MCP 相关术语速查

| 术语 | 含义（小白版） |
|------|----------------|
| **MCP** | Model Context Protocol，让 AI 能“按菜单点菜”一样调用你服务的协议。 |
| **MCP Server** | 本仓库就是这个角色：提供工具列表 + 执行工具。 |
| **MCP Client** | Cursor、Claude Desktop、Postman 等，向 Server 要菜单、发点菜请求。 |
| **Tool（工具）** | 你暴露的一个能力，如 `createIncident`、`listRunbooks`。 |
| **tools/list** | JSON-RPC 方法名：客户端问“你有哪些工具？” |
| **tools/call** | JSON-RPC 方法名：客户端说“我要调用工具 X，参数是 Y”。 |
| **JSON-RPC** | 一种请求格式：`{"jsonrpc":"2.0","id":1,"method":"tools/list"}` 等。 |
| **STATELESS** | 无会话：每次请求独立，不需要 Session ID，适合 Postman 单次请求。 |
| **STREAMABLE** | 有会话：先 Initialize 拿 Session ID，后续请求都要带该 ID。 |

### 0.8 延伸阅读

- 官方介绍：[Model Context Protocol](https://modelcontextprotocol.io/)  
- 本仓库：下面章节会写**怎么运行、怎么用 Postman 测、工具列表、配置项**等。

---

## 1. 环境要求

- **JDK**: 21+
- **Maven**: 3.6.0+（推荐 3.6.3+）
- **Spring Boot**: **4.0.1**（Spring AI 2.0 要求）
- **Spring AI**: **2.0.0-M2**（通过 `spring-ai-bom` 管理）

---

## 2. 运行方式

### 2.1 编译项目

```bash
mvn clean compile
```

### 2.2 运行服务

```bash
mvn spring-boot:run
```

或者：

```bash
mvn clean package
java -jar target/spring-ai-mcp-demo-0.1.0.jar
```

### 2.3 访问端点

- **服务端口**: `8090`
- **MCP 入口**: `POST http://localhost:8090/mcp`（JSON-RPC，当前为 STATELESS，无需会话）
- **DeepSeek 聊天**（往表里插数据）: `POST http://localhost:8090/api/chat`，Body: `{"message": "帮我在表里插一条数据，标题是xxx，内容是xxx"}`，需配置 `DEEPSEEK_API_KEY`
- **健康检查**: `http://localhost:8090/actuator/health`
- **指标监控**: `http://localhost:8090/actuator/metrics`
- **数据库**：MySQL，连接信息见 `application.yml` 中 `spring.datasource`

---

## 3. MCP 能力概览

### 3.1 事件工具（IncidentToolService）

- `createIncident` - 创建事件
- `getIncident` - 获取事件详情
- `listIncidents` - 列出事件列表
- `assignIncident` - 分配事件
- `updateIncidentStatus` - 更新事件状态
- `addIncidentNote` - 添加事件备注
- `updateIncidentSla` - 更新事件 SLA
- `incidentStats` - 事件统计

### 3.2 运行手册工具（RunbookToolService）

- `listRunbooks` - 列出运行手册
- `getRunbook` - 获取运行手册详情
- `searchRunbooks` - 搜索运行手册
- `recommendRunbookForIncident` - 为事件推荐运行手册

### 3.3 流程工具（WorkflowToolService）

- `scheduleFollowUp` - 调度跟进任务
- `cancelFollowUp` - 取消跟进任务
- `bulkUpdateStatus` - 批量更新状态
- `planMitigation` - 制定缓解计划
- `correlateIncidents` - 关联事件

### 3.4 数据表工具（DataRecordToolService）— 往表中插入数据

- `insertData` - 往 **data_record** 表插入一条数据（标题 + 内容）
- `listData` - 查询 data_record 表最近插入的数据列表

**表结构**：MySQL，表名 `data_record`，字段：`id`、`title`、`content`、`created_at`（JPA 自动建表/更新，`ddl-auto: update`）。  
**接入 DeepSeek**：配置 `DEEPSEEK_API_KEY` 后，调用 `POST /api/chat`，用自然语言说“帮我在表里插一条数据，标题是xxx，内容是xxx”，DeepSeek 会调用 `insertData` 工具写入表。

---

## 4. MCP Tool 参数示例

> MCP 客户端调用时，只需传 **arguments**（下面仅展示 arguments 内容）。

### 创建事件

```json
{
  "title": "API 延迟飙升",
  "description": "核心 API 95% 响应超过 2s",
  "severity": "高",
  "tags": ["api", "latency"],
  "createdBy": "oncall",
  "slaMinutes": 60,
  "metadata": {"service": "gateway", "region": "ap-sg"}
}
```

### 更新状态

```json
{
  "incidentId": "INC-1001-abc123",
  "status": "处理中",
  "actor": "sre-lee",
  "note": "启用缓存降级"
}
```

### 调度跟进

```json
{
  "incidentId": "INC-1001-abc123",
  "delaySeconds": 120,
  "message": "2 分钟后复查恢复情况",
  "actor": "bot"
}
```

### 推荐 Runbook

```json
{
  "incidentId": "INC-1001-abc123"
}
```

### 往表中插入数据（insertData）

```json
{
  "title": "今日待办",
  "content": "完成 MCP 文档、联调 DeepSeek"
}
```

### 查询表数据列表（listData）

```json
{
  "limit": 10
}
```

---

## 5. 代码结构（MCP 在本项目中的位置）

请求从客户端到工具的大致路径：

```
  Postman / Cursor 等
        │
        │  POST /mcp  (JSON-RPC: tools/list 或 tools/call)
        ▼
  Spring AI MCP WebMVC 自动配置（接收请求、解析 JSON-RPC）
        │
        │  根据 method 分发：tools/list → 收集工具列表；tools/call → 按 name 找工具并执行
        ▼
  McpToolsConfig 注册的 ToolCallbackProvider（incidentTools / runbookTools / workflowTools）
        │
        │  把请求交给对应 Service 的 @Tool 方法
        ▼
  IncidentToolService / RunbookToolService / WorkflowToolService（你的业务逻辑）
```

源码目录：

```
src/main/java/com/cursor/mcpdemo
|-- config
|   `-- McpToolsConfig.java         # MCP 工具注册（使用 Spring AI 自动配置）
|-- domain
|   |-- Incident.java                # 事件实体
|   |-- IncidentEvent.java          # 事件变更记录
|   |-- IncidentSeverity.java        # 事件严重程度枚举
|   |-- IncidentStatus.java          # 事件状态枚举
|   `-- Runbook.java                 # 运行手册实体
|-- store
|   `-- IncidentStore.java           # 内存事件仓库
|-- tool
|   |-- IncidentToolService.java     # 事件工具服务
|   |-- RunbookToolService.java      # 运行手册工具服务
|   |-- WorkflowToolService.java     # 流程工具服务
|   `-- DataRecordToolService.java   # 往 data_record 表插入/查询（MCP + DeepSeek）
|-- service
|   `-- DataRecordService.java       # data_record 表业务
|-- repository
|   `-- DataRecordRepository.java    # JPA 仓库
|-- controller
|   `-- ChatController.java          # DeepSeek 聊天接口 POST /api/chat
|-- util
|   `-- ToolResponses.java           # 工具响应工具类
`-- McpDemoApplication.java          # Spring Boot 主类
```

---

## 6. 配置说明

### 6.1 application.yml

项目使用 Spring AI 自带的 MCP WebMVC 自动配置，主要配置项：

```yaml
spring:
  ai:
    mcp:
      server:
        name: incident-mcp-demo      # MCP 服务器名称（客户端可见）
        version: 0.1.0               # 版本
        protocol: STATELESS          # 无会话，适合 Postman 单次请求；可选 STREAMABLE（需 Session ID）
        enabled: true                # 是否启用（默认 true）
```

### 6.2 MCP 自动配置

项目使用 `spring-ai-starter-mcp-server-webmvc` 提供的自动配置：

- **自动配置类**: `McpWebMvcServerAutoConfiguration`
- **协议**: 当前为 **STATELESS**（无会话，每个请求独立）
- **端点**: `POST /mcp`，请求体为 JSON-RPC 2.0（如 `initialize`、`tools/list`、`tools/call`）
- **工具注册**: 通过 `ToolCallbackProvider` Bean 自动发现并暴露为 MCP 工具

### 6.3 工具注册方式

工具通过 `McpToolsConfig` 中的 `ToolCallbackProvider` Bean 注册：

```java
@Bean("incidentTools")
public ToolCallbackProvider incidentTools(IncidentToolService service) {
    return MethodToolCallbackProvider.builder().toolObjects(service).build();
}
```

---

## 7. 技术栈

- **Spring Boot**: **4.0.1**
- **Spring AI**: **2.0.0-M2**（[spring-ai-bom](https://mvnrepository.com/artifact/org.springframework.ai/spring-ai-bom)）
- **MCP**: 由 `spring-ai-starter-mcp-server-webmvc` 自动引入（版本由 BOM 管理）
- **Java**: 21（使用 Record、Switch 表达式等现代特性）

---

## 8. DeepSeek 接入说明（往表中插入数据）

### 8.1 配置 API Key

DeepSeek 聊天接口需要 API Key，任选其一：

- **环境变量**（推荐）：`DEEPSEEK_API_KEY=sk-xxx`，再启动应用。
- **application.yml**：`spring.ai.deepseek.api-key: sk-xxx`（勿提交到仓库）。

未配置时，调用 `/api/chat` 会因调用 DeepSeek 失败而报错。

### 8.2 使用方式

1. **Postman / curl**：`POST http://localhost:8090/api/chat`，Body: `{"message": "帮我在表里插一条数据，标题是：今日待办，内容是：完成 MCP 文档"}`。
2. DeepSeek 会理解意图，调用 MCP 工具 **insertData**，把数据写入 **data_record** 表（MySQL）。
3. 返回的 `reply` 中会包含插入结果（如 id、title、content、createdAt）。

### 8.3 数据表与 MCP 工具

- **表**：`data_record`（MySQL），字段：`id`、`title`、`content`、`created_at`。
- **MCP 工具**：`insertData(title, content)`、`listData(limit)`，既可通过 MCP 客户端（如 Postman 发 `tools/call`）调用，也可通过 DeepSeek 聊天由模型自动调用。

---

## 8.4 插入数据的底层原理（加深对 MCP 的理解）

本节从**请求进来到数据落库**的完整链路，说明“插入数据”是怎么做的，以及 **MCP 和 DeepSeek 各自扮演什么角色**。

### 一、两条入口，同一套“工具”

本项目中，**往 `data_record` 表插入数据**有两条入口，但**底层执行的都是同一套工具**：

| 入口 | 谁在调用 | 协议/方式 |
|------|----------|------------|
| **POST /mcp**（JSON-RPC） | Postman、Cursor 等 MCP 客户端 | 客户端发 `tools/call`，传 `name=insertData`、`arguments={title, content}` |
| **POST /api/chat**（自然语言） | 用户发一句话 | DeepSeek 理解意图后，由 Spring AI 的 ChatClient **内部**调用同一个 `insertData` 工具 |

也就是说：**MCP 协议负责“对外暴露工具 + 约定调用格式”；DeepSeek 聊天只是“另一种调用方式”——通过大模型把自然语言转成对 `insertData` 的调用，调用的仍是同一个 Java 方法。**

---

### 二、从请求到数据库的完整调用链

下面用**分层**的方式，从外到内说明一次“插入数据”请求会经过哪些组件、每一层在干什么。

```
┌─────────────────────────────────────────────────────────────────────────────┐
│  1. 入口层（HTTP）                                                           │
├─────────────────────────────────────────────────────────────────────────────┤
│  • 方式 A：POST /mcp  Body: {"method":"tools/call","params":{"name":"insertData","arguments":{...}}}  │
│  • 方式 B：POST /api/chat  Body: {"message":"帮我在表里插一条数据，标题是xxx，内容是xxx"}              │
└─────────────────────────────────────────────────────────────────────────────┘
                                        │
                                        ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│  2. 协议 / 路由层                                                            │
├─────────────────────────────────────────────────────────────────────────────┤
│  • 方式 A：Spring AI MCP WebMVC 接收 POST /mcp，解析 JSON-RPC，发现 method=tools/call、              │
│            name=insertData → 根据“工具名”路由到对应的 ToolCallback（见下文）。                        │
│  • 方式 B：ChatController 收到 message → 交给 ChatClient；ChatClient 把用户消息发给 DeepSeek；       │
│            DeepSeek 返回“要调用工具 insertData，参数为 title=xxx, content=xxx”；                    │
│            Spring AI 根据工具名执行对应的 ToolCallback（同样是 insertData）。                         │
└─────────────────────────────────────────────────────────────────────────────┘
                                        │
                                        ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│  3. 工具层（MCP 的“工具” = 一个可被按名字调用的方法）                         │
├─────────────────────────────────────────────────────────────────────────────┤
│  • 类：DataRecordToolService（tool/DataRecordToolService.java）              │
│  • 方法：insertData(String title, String content)  带 @Tool 注解              │
│  • 作用：校验参数（如 title 非空），然后调用 DataRecordService.insert(...)，                        │
│          把返回的实体转成 JSON 字符串（含 success、table、data、timestamp），返回给调用方。           │
│  • 为何叫“MCP 工具”：在 McpToolsConfig 里，通过 MethodToolCallbackProvider 把该类的实例              │
│    注册为 ToolCallbackProvider（Bean 名 dataRecordTools）；MCP 服务端在“工具列表”里暴露              │
│    insertData 的名字和参数 schema，在 tools/call 时按 name 找到并执行这个方法。                     │
└─────────────────────────────────────────────────────────────────────────────┘
                                        │
                                        ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│  4. 业务层                                                                   │
├─────────────────────────────────────────────────────────────────────────────┤
│  • 类：DataRecordService（service/DataRecordService.java）                   │
│  • 方法：insert(String title, String content)                                 │
│  • 作用：new DataRecord()，setTitle/setContent，调用 repository.save(record)；                    │
│          插入成功后打日志：[插入表] 表名=data_record, id=..., title=..., contentLength=...          │
└─────────────────────────────────────────────────────────────────────────────┘
                                        │
                                        ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│  5. 持久层                                                                   │
├─────────────────────────────────────────────────────────────────────────────┤
│  • 接口：DataRecordRepository（repository/DataRecordRepository.java）         │
│          继承 JpaRepository<DataRecord, Long>                                │
│  • 表：data_record（由 JPA 根据实体 DataRecord 建表，ddl-auto=update）        │
│  • 实体：DataRecord（domain/DataRecord.java） 字段：id, title, content, created_at                  │
│  • save(record) 时：Hibernate 生成 INSERT INTO data_record (title, content, created_at) ...         │
│                    并写入你配置的 MySQL（application.yml 里 spring.datasource.url 指定的库）。       │
└─────────────────────────────────────────────────────────────────────────────┘
                                        │
                                        ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│  6. 数据库                                                                   │
├─────────────────────────────────────────────────────────────────────────────┤
│  • MySQL 中库名由 jdbc:mysql://.../xxx 决定，表名为 data_record。             │
│  • 插入成功后，可在 MySQL 中执行：SELECT * FROM data_record; 查看数据。        │
└─────────────────────────────────────────────────────────────────────────────┘
```

总结：**插入数据** = 入口（/mcp 或 /api/chat）→ 协议/路由（MCP 或 ChatClient 按“工具名”路由）→ **工具层**（`DataRecordToolService.insertData`）→ **业务层**（`DataRecordService.insert`）→ **持久层**（`DataRecordRepository.save` + 实体 `DataRecord`）→ **MySQL 表 `data_record`**。

---

### 三、MCP 在这一过程中具体做了什么？

可以拆成三件事，对应你对 MCP 的“深刻理解”：

1. **工具注册与发现**  
   你在代码里用 `@Tool` 写了一个方法（如 `insertData`），并在 `McpToolsConfig` 里用 `MethodToolCallbackProvider.builder().toolObjects(service).build()` 把它注册成 MCP 的“工具”。  
   → 当客户端发 **tools/list** 时，MCP 服务端会返回“工具列表”，其中就包含 `insertData` 的名字、描述、参数 schema（title、content 等）。  
   → **所以：MCP 让“插入数据”这个能力变成“可被发现的、带 schema 的工具”，而不是一个写死的 HTTP 路径。**

2. **按名调用（tools/call）**  
   客户端发 **tools/call** 时，只传 `name=insertData` 和 `arguments={...}`。  
   → MCP 服务端根据 `name` 找到注册好的 ToolCallback，把 `arguments` 转成方法参数，**反射调用** `DataRecordToolService.insertData(title, content)`。  
   → **所以：MCP 约定的是“调用方式”（JSON-RPC + 工具名 + 参数），真正“插表”的逻辑完全是你写的 Java 代码；MCP 不关心你里面是 JPA 还是 JDBC，只负责把调用“路由到正确的方法”。**

3. **与 DeepSeek 的衔接**  
   `/api/chat` 用的是 Spring AI 的 ChatClient，ChatClient 在请求 DeepSeek 时会把“本应用注册的 ToolCallback”（如 dataRecordTools）以“函数/工具”的形式发给 DeepSeek。  
   → DeepSeek 根据用户自然语言决定“要调用 insertData，参数是 title=xxx, content=xxx”，在响应里带上 tool_calls。  
   → Spring AI 收到后，**在本地执行**对应的 ToolCallback（还是同一个 `insertData` 方法），把执行结果再发给 DeepSeek，由 DeepSeek 生成最终的自然语言回复。  
   → **所以：DeepSeek 只负责“理解用户 + 决定调哪个工具、参数填什么”；真正插表、访问数据库的，始终是你本地的 MCP 工具实现。**

---

### 四、对应到本仓库的代码位置（便于你翻源码）

| 层次 | 作用 | 文件/位置 |
|------|------|-----------|
| MCP 协议入口 | 接收 POST /mcp，解析 JSON-RPC，分发 tools/call | Spring AI 自动配置（spring-ai-starter-mcp-server-webmvc），无需你写 |
| 工具注册 | 把 insertData / listData 注册为 MCP 工具 | `config/McpToolsConfig.java` 的 `dataRecordTools` Bean |
| 工具实现 | 参数校验、调 Service、返回 JSON 字符串 | `tool/DataRecordToolService.java` 的 `insertData`、`listData` |
| 业务逻辑 | 组实体、打日志、调 Repository | `service/DataRecordService.java` 的 `insert` |
| 持久化 | JPA 仓库与实体，对应表 data_record | `repository/DataRecordRepository.java`、`domain/DataRecord.java` |
| DeepSeek 入口 | 自然语言 → ChatClient → 内部调工具 | `controller/ChatController.java` 的 `/api/chat` |

看完这一节再去看代码，你会更清楚：**MCP 是“协议层 + 路由层”，真正“往哪张表、插什么”完全由你的 Tool + Service + Repository + 实体决定。**

---

## 9. 可扩展方向

1. **数据持久化**: 接入数据库（MySQL / PostgreSQL）
2. **MCP 资源**: 增加 MCP Resource 支持
3. **MCP 提示**: 增加 MCP Prompt 支持
4. **安全增强**: 在 Tool 中增加鉴权与限流
5. **可观测性**: 增加审计日志与链路追踪
6. **测试覆盖**: 增加单元测试和集成测试

---

## 10. 常见问题

### 10.1 编译失败

**问题**: Maven 编译失败，提示找不到符号或版本不兼容。

**解决**:
- 确保 JDK 版本为 21+
- 确保 Maven 版本为 3.6.0+（推荐 3.6.3+）
- 运行 `mvn clean compile` 重新编译

### 10.2 MCP 端点无法访问

**问题**: 访问 `/mcp` 端点返回 404。

**解决**:
- 检查 `spring.ai.mcp.server.enabled` 是否为 `true`（默认已启用）
- 确认 Spring AI MCP 依赖已正确引入
- 查看应用启动日志，确认 MCP 服务器已初始化

### 10.3 工具未注册

**问题**: MCP 客户端无法发现工具。

**解决**:
- 确认 `ToolCallbackProvider` Bean 已正确注册
- 检查工具方法是否使用了正确的注解（如 `@Tool`）
- 查看应用日志，确认工具注册信息

---

### 10.4 DeepSeek 聊天报错

**问题**：调用 `/api/chat` 报错或超时。

**解决**：配置 `DEEPSEEK_API_KEY` 环境变量或 `spring.ai.deepseek.api-key`；确认网络可访问 DeepSeek API。

---

## 11. 学习 Spring AI 建议

本项目使用 **Spring AI 2.0.0-M2**，可重点学习：

1. **BOM 管理**：在 `pom.xml` 中通过 `spring-ai-bom` 统一管理 Spring AI 及 MCP 相关依赖版本。
2. **MCP 工具**：使用 `@Tool`、`@ToolParam` 定义工具方法，通过 `ToolCallbackProvider` 注册到 MCP Server。
3. **自动配置**：无需手写 MCP 传输层，`spring-ai-starter-mcp-server-webmvc` 提供 SSE 端点与工具发现。
4. **配置**：`spring.ai.mcp.server.*` 控制 MCP 服务器名称、版本、协议类型等。

升级方式：将 `spring-ai.version` 改为 `2.0.0-M2` 即可（见 `pom.xml` 的 `dependencyManagement`）。

---

## 12. 参考资源

- [Spring AI 官方文档](https://docs.spring.io/spring-ai/reference/)
- [Spring AI BOM 2.0.0-M2 (Maven Repository)](https://mvnrepository.com/artifact/org.springframework.ai/spring-ai-bom/2.0.0-M2)
- [Model Context Protocol 规范](https://modelcontextprotocol.io/)
- [MCP Java SDK](https://github.com/modelcontextprotocol/java-sdk)
