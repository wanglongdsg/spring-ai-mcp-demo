package com.cursor.mcpdemo.tool;

import com.cursor.mcpdemo.domain.Incident;
import com.cursor.mcpdemo.domain.IncidentEvent;
import com.cursor.mcpdemo.domain.IncidentSeverity;
import com.cursor.mcpdemo.domain.IncidentStatus;
import com.cursor.mcpdemo.store.IncidentStore;
import com.cursor.mcpdemo.util.ToolResponses;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class WorkflowToolService {

    private final IncidentStore store;
    private final ObjectMapper objectMapper;
    private final ScheduledExecutorService scheduler;
    private final Map<String, ScheduledFuture<?>> followUps = new ConcurrentHashMap<>();

    public WorkflowToolService(IncidentStore store, ObjectMapper objectMapper) {
        this.store = store;
        this.objectMapper = objectMapper;
        this.scheduler = Executors.newScheduledThreadPool(2, Thread.ofVirtual().factory());
    }

    @Tool(returnDirect = true, description = "为事件安排延迟跟进，到期后自动写入时间线。")
    public String scheduleFollowUp(
            @ToolParam(description = "事件ID") String incidentId,
            @ToolParam(description = "延迟秒数") Integer delaySeconds,
            @ToolParam(description = "跟进内容") String message,
            @ToolParam(description = "操作人") String actor) {
        if (delaySeconds == null || delaySeconds < 1) {
            return ToolResponses.error(objectMapper, "延迟秒数必须大于等于 1");
        }
        Incident incident = store.get(incidentId).orElse(null);
        if (incident == null) {
            return ToolResponses.error(objectMapper, "事件不存在: " + incidentId);
        }
        ScheduledFuture<?> previous = followUps.remove(incidentId);
        if (previous != null) {
            previous.cancel(false);
        }
        ScheduledFuture<?> future = scheduler.schedule(() -> {
            store.addNote(incident, actor, message);
        }, delaySeconds, TimeUnit.SECONDS);
        followUps.put(incidentId, future);
        return ToolResponses.success(objectMapper, Map.of(
                "事件ID", incidentId,
                "延迟秒数", delaySeconds,
                "跟进内容", message
        ));
    }

    @Tool(returnDirect = true, description = "取消事件的跟进任务。")
    public String cancelFollowUp(@ToolParam(description = "事件ID") String incidentId) {
        ScheduledFuture<?> future = followUps.remove(incidentId);
        if (future == null) {
            return ToolResponses.error(objectMapper, "该事件没有已安排的跟进: " + incidentId);
        }
        future.cancel(false);
        return ToolResponses.success(objectMapper, Map.of(
                "事件ID", incidentId,
                "已取消", true
        ));
    }

    @Tool(returnDirect = true, description = "批量更新事件状态。")
    public String bulkUpdateStatus(
            @ToolParam(description = "事件ID列表") List<String> incidentIds,
            @ToolParam(description = "目标状态") String status,
            @ToolParam(description = "操作人") String actor) {
        if (incidentIds == null || incidentIds.isEmpty()) {
            return ToolResponses.error(objectMapper, "事件ID列表不能为空");
        }
        IncidentStatus st;
        try {
            st = IncidentStatus.from(status);
        } catch (IllegalArgumentException ex) {
            return ToolResponses.error(objectMapper, "非法状态: " + status);
        }
        List<String> updated = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (String id : incidentIds) {
            Incident incident = store.get(id).orElse(null);
            if (incident == null) {
                missing.add(id);
            } else {
                store.updateStatus(incident, st, actor, "批量更新");
                updated.add(id);
            }
        }
        return ToolResponses.success(objectMapper, Map.of(
                "已更新", updated,
                "未找到", missing
        ));
    }

    @Tool(returnDirect = true, description = "基于严重等级与标签生成处置计划。")
    public String planMitigation(@ToolParam(description = "事件ID") String incidentId) {
        Incident incident = store.get(incidentId).orElse(null);
        if (incident == null) {
            return ToolResponses.error(objectMapper, "事件不存在: " + incidentId);
        }
        List<String> steps = new ArrayList<>();
        IncidentSeverity severity = incident.getSeverity();
        if (severity == IncidentSeverity.CRITICAL) {
            steps.add("将流量切换到备用集群");
            steps.add("通知值班人员与负责人");
            steps.add("冻结相关发布与变更");
        } else if (severity == IncidentSeverity.HIGH) {
            steps.add("扩大监控维度并确认影响范围");
            steps.add("启用降级策略并准备回滚");
        } else {
            steps.add("收集日志与指标");
            steps.add("定位根因并评估修复窗口");
        }
        if (incident.getTags().contains("安全")) {
            steps.add("触发安全响应与审计");
        }
        return ToolResponses.success(objectMapper, Map.of(
                "事件ID", incidentId,
                "步骤", steps
        ));
    }

    @Tool(returnDirect = true, description = "基于标签与关键词进行事件关联分析。")
    public String correlateIncidents(
            @ToolParam(description = "关键词（可选）") String keyword,
            @ToolParam(description = "限制数量（默认 10）") Integer limit) {
        String needle = keyword == null ? null : keyword.toLowerCase(Locale.ROOT);
        List<Incident> incidents = store.all().stream()
                .sorted(Comparator.comparing(Incident::getUpdatedAt).reversed())
                .limit(limit == null ? 10 : limit)
                .collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> items = new ArrayList<>();
        for (Incident incident : incidents) {
            boolean match = needle == null || needle.isBlank()
                    || incident.getTitle().toLowerCase(Locale.ROOT).contains(needle)
                    || incident.getDescription().toLowerCase(Locale.ROOT).contains(needle)
                    || incident.getTags().stream().anyMatch(tag -> tag.toLowerCase(Locale.ROOT).contains(needle));
            if (!match) {
                continue;
            }
            items.add(Map.of(
                    "事件ID", incident.getId(),
                    "标题", incident.getTitle(),
                    "标签", incident.getTags(),
                    "状态", incident.getStatus().displayName(),
                    "严重等级", incident.getSeverity().displayName()
            ));
        }
        result.put("数量", items.size());
        result.put("列表", items);
        return ToolResponses.success(objectMapper, result);
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdown();
    }
}
