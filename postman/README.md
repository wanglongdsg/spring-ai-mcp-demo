# Postman 测试说明

## 导入步骤

1. 打开 Postman，点击 **Import**。
2. 选择并导入以下两个文件（可多选一次导入）：
   - `spring-ai-mcp-demo.postman_collection.json`（请求集合）
   - `spring-ai-mcp-demo.postman_environment.json`（环境变量）
3. 右上角环境下拉框选择 **Spring AI MCP Demo - Local**。
4. 确保本机已启动服务：`mvn spring-boot:run`，端口 **8090**。

## 集合说明

| 分组 | 说明 |
|------|------|
| **Actuator** | 健康检查、Info、Metrics、Prometheus，均为 GET。 |
| **MCP JSON-RPC** | MCP 协议：Initialize → tools/list → tools/call（POST，JSON-RPC 2.0）。**必须带请求头**：`Content-Type: application/json` 和 `Accept: text/event-stream, application/json`，集合中已配置。 |

## 协议说明（重要）

项目使用 **STATELESS** 协议（`spring.ai.mcp.server.protocol=STATELESS`），**无会话**，每个请求独立，适合 Postman 单次发请求测试，**不需要 Session ID**。

若改为 **STREAMABLE**，则必须先调 **Initialize**，从响应头拿到 `Mcp-Session-Id`，之后所有请求都要带请求头 `Mcp-Session-Id: <该值>`，否则会报 "Session ID missing"。

## MCP 请求若返回 404 或 400

1. **确认协议**：`application.yml` 中为 `protocol: STATELESS`（推荐 Postman 测试）。
2. **确认已重启应用**：改配置后需重新 `mvn spring-boot:run`。
3. **请求头**：必须带 `Accept: text/event-stream, application/json`，集合中已配置。

## 建议测试顺序

1. **Actuator → Health**：确认服务已启动。
2. **MCP → Initialize**：若成功，再测 **tools/list**。
3. **MCP → tools/call - createIncident**：创建一条事件，记下返回的 `incidentId`。
4. 将后续 **getIncident / updateIncidentStatus / recommendRunbookForIncident** 等请求里的 `incidentId` 替换为刚创建的 ID，再发送请求。

## 变量

- **base_url**：默认 `http://localhost:8090`，可在环境中修改（如改成远程地址）。
