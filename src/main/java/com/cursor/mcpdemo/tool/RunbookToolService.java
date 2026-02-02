package com.cursor.mcpdemo.tool;

import com.cursor.mcpdemo.domain.Incident;
import com.cursor.mcpdemo.domain.Runbook;
import com.cursor.mcpdemo.store.IncidentStore;
import com.cursor.mcpdemo.util.ToolResponses;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class RunbookToolService {

    private final List<Runbook> runbooks;
    private final IncidentStore incidentStore;
    private final ObjectMapper objectMapper;

    public RunbookToolService(IncidentStore incidentStore, ObjectMapper objectMapper) {
        this.incidentStore = incidentStore;
        this.objectMapper = objectMapper;
        this.runbooks = seedRunbooks();
    }

    @Tool(returnDirect = true, description = "列出运行手册（可按标签过滤）。")
    public String listRunbooks(@ToolParam(description = "标签过滤（可选）") String tag) {
        List<Map<String, Object>> list = runbooks.stream()
                .filter(rb -> tag == null || tag.isBlank() || rb.tags().contains(tag))
                .map(this::toRunbookView)
                .collect(Collectors.toList());
        return ToolResponses.success(objectMapper, list);
    }

    @Tool(returnDirect = true, description = "获取运行手册详情。")
    public String getRunbook(@ToolParam(description = "运行手册ID") String id) {
        return runbooks.stream()
                .filter(rb -> rb.id().equalsIgnoreCase(id))
                .findFirst()
                .map(rb -> ToolResponses.success(objectMapper, toRunbookView(rb)))
                .orElseGet(() -> ToolResponses.error(objectMapper, "运行手册不存在: " + id));
    }

    @Tool(returnDirect = true, description = "按关键词搜索运行手册（标题/描述/步骤）。")
    public String searchRunbooks(@ToolParam(description = "关键词") String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return ToolResponses.error(objectMapper, "关键词不能为空");
        }
        String needle = keyword.toLowerCase(Locale.ROOT);
        List<Map<String, Object>> list = runbooks.stream()
                .filter(rb -> rb.title().toLowerCase(Locale.ROOT).contains(needle)
                        || rb.description().toLowerCase(Locale.ROOT).contains(needle)
                        || rb.steps().stream().anyMatch(step -> step.toLowerCase(Locale.ROOT).contains(needle)))
                .map(this::toRunbookView)
                .collect(Collectors.toList());
        return ToolResponses.success(objectMapper, list);
    }

    @Tool(returnDirect = true, description = "根据事件上下文推荐运行手册。")
    public String recommendRunbookForIncident(@ToolParam(description = "事件ID") String incidentId) {
        Incident incident = incidentStore.get(incidentId).orElse(null);
        if (incident == null) {
            return ToolResponses.error(objectMapper, "事件不存在: " + incidentId);
        }
        List<Map<String, Object>> matches = runbooks.stream()
                .filter(rb -> rb.tags().stream().anyMatch(tag -> incident.getTags().contains(tag)))
                .map(this::toRunbookView)
                .collect(Collectors.toList());
        if (matches.isEmpty()) {
            String severityTag = severityTag(incident);
            matches = runbooks.stream()
                    .filter(rb -> rb.tags().contains(severityTag))
                    .map(this::toRunbookView)
                    .collect(Collectors.toList());
        }
        return ToolResponses.success(objectMapper, Map.of(
                "事件ID", incidentId,
                "推荐列表", matches
        ));
    }

    private List<Runbook> seedRunbooks() {
        List<Runbook> list = new ArrayList<>();
        list.add(new Runbook(
                "rb-ddos-001",
                "拒绝服务缓解流程",
                "用于识别与缓解拒绝服务攻击的标准流程",
                List.of(
                        "确认入口流量异常与目标服务",
                        "切换流量至清洗或应用防火墙",
                        "启用更严格的速率限制与黑名单",
                        "回滚或扩容边缘资源",
                        "复盘并更新流量基线"
                ),
                Set.of("网络", "攻击", "严重")
        ));
        list.add(new Runbook(
                "rb-auth-002",
                "认证异常排查",
                "处理登录失败率提升或异常 401/403 的流程",
                List.of(
                        "核对鉴权服务健康状况",
                        "检查近期发布与配置变更",
                        "回滚到上一个稳定版本",
                        "核验第三方身份源可用性",
                        "补充监控与报警规则"
                ),
                Set.of("认证", "安全", "高")
        ));
        list.add(new Runbook(
                "rb-data-003",
                "数据一致性修复",
                "当发现数据滞后/错乱时的处理手册",
                List.of(
                        "确认数据源与同步链路",
                        "隔离受影响用户与业务操作",
                        "执行校验脚本并生成差异",
                        "按差异回补数据",
                        "验证并复盘"
                ),
                Set.of("数据", "中")
        ));
        return list;
    }

    private String severityTag(Incident incident) {
        return incident.getSeverity().displayName();
    }

    private Map<String, Object> toRunbookView(Runbook runbook) {
        return Map.of(
                "运行手册ID", runbook.id(),
                "标题", runbook.title(),
                "描述", runbook.description(),
                "步骤", runbook.steps(),
                "标签", runbook.tags()
        );
    }
}
