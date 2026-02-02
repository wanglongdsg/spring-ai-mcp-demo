package com.cursor.mcpdemo.tool;

import com.cursor.mcpdemo.domain.Incident;
import com.cursor.mcpdemo.domain.IncidentSeverity;
import com.cursor.mcpdemo.domain.IncidentStatus;
import com.cursor.mcpdemo.store.IncidentStore;
import com.cursor.mcpdemo.util.ToolResponses;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class IncidentToolService {

    private final IncidentStore store;
    private final ObjectMapper objectMapper;

    public IncidentToolService(IncidentStore store, ObjectMapper objectMapper) {
        this.store = store;
        this.objectMapper = objectMapper;
    }

    @Tool(returnDirect = true, description = "创建新事件并返回摘要。")
    public String createIncident(
            @ToolParam(description = "事件标题") String title,
            @ToolParam(description = "事件描述") String description,
            @ToolParam(description = "严重等级：严重/高/中/低") String severity,
            @ToolParam(description = "标签列表（可选）") List<String> tags,
            @ToolParam(description = "创建人/触发者") String createdBy,
            @ToolParam(description = "时限分钟数（可选）") Integer slaMinutes,
            @ToolParam(description = "附加元数据（键值对，可选）") Map<String, String> metadata) {
        try {
            IncidentSeverity sev = IncidentSeverity.from(severity);
            if (sev == null) {
                return ToolResponses.error(objectMapper, "严重等级不能为空");
            }
            Duration sla = slaMinutes == null ? null : Duration.ofMinutes(Math.max(1, slaMinutes));
            Incident incident = store.create(title, description, sev, createdBy, tags == null ? Set.of() : Set.copyOf(tags), metadata, sla);
            return ToolResponses.success(objectMapper, toSummary(incident));
        } catch (IllegalArgumentException ex) {
            return ToolResponses.error(objectMapper, "非法严重等级: " + severity);
        }
    }

    @Tool(returnDirect = true, description = "获取事件详情（包含时间线）。")
    public String getIncident(@ToolParam(description = "事件ID") String incidentId) {
        return store.get(incidentId)
                .map(incident -> ToolResponses.success(objectMapper, toDetail(incident)))
                .orElseGet(() -> ToolResponses.error(objectMapper, "事件不存在: " + incidentId));
    }

    @Tool(returnDirect = true, description = "按条件列出事件摘要。")
    public String listIncidents(
            @ToolParam(description = "状态过滤（可选）") String status,
            @ToolParam(description = "严重等级过滤（可选）") String severity,
            @ToolParam(description = "负责人过滤（可选）") String assignee,
            @ToolParam(description = "标签过滤（可选）") String tag,
            @ToolParam(description = "返回数量限制，默认 20") Integer limit) {
        IncidentStatus st = null;
        IncidentSeverity sev = null;
        try {
            if (status != null && !status.isBlank()) {
                st = IncidentStatus.from(status);
            }
            if (severity != null && !severity.isBlank()) {
                sev = IncidentSeverity.from(severity);
            }
        } catch (IllegalArgumentException ex) {
            return ToolResponses.error(objectMapper, "状态或严重等级不合法");
        }
        List<Map<String, Object>> list = store.list(st, sev, assignee, tag, limit == null ? 20 : limit).stream()
                .map(this::toSummary)
                .collect(Collectors.toList());
        return ToolResponses.success(objectMapper, list);
    }

    @Tool(returnDirect = true, description = "分配事件负责人。")
    public String assignIncident(
            @ToolParam(description = "事件ID") String incidentId,
            @ToolParam(description = "负责人") String assignee,
            @ToolParam(description = "操作人") String actor) {
        return store.get(incidentId)
                .map(incident -> ToolResponses.success(objectMapper, toSummary(store.assign(incident, assignee, actor))))
                .orElseGet(() -> ToolResponses.error(objectMapper, "事件不存在: " + incidentId));
    }

    @Tool(returnDirect = true, description = "更新事件状态并记录备注。")
    public String updateIncidentStatus(
            @ToolParam(description = "事件ID") String incidentId,
            @ToolParam(description = "状态：未处理/已确认/处理中/已解决/已关闭") String status,
            @ToolParam(description = "操作人") String actor,
            @ToolParam(description = "备注（可选）") String note) {
        IncidentStatus st;
        try {
            st = IncidentStatus.from(status);
        } catch (IllegalArgumentException ex) {
            return ToolResponses.error(objectMapper, "非法状态: " + status);
        }
        if (st == null) {
            return ToolResponses.error(objectMapper, "状态不能为空");
        }
        return store.get(incidentId)
                .map(incident -> ToolResponses.success(objectMapper, toSummary(store.updateStatus(incident, st, actor, note))))
                .orElseGet(() -> ToolResponses.error(objectMapper, "事件不存在: " + incidentId));
    }

    @Tool(returnDirect = true, description = "追加事件备注（时间线记录）。")
    public String addIncidentNote(
            @ToolParam(description = "事件ID") String incidentId,
            @ToolParam(description = "备注内容") String note,
            @ToolParam(description = "操作人") String actor) {
        return store.get(incidentId)
                .map(incident -> ToolResponses.success(objectMapper, toDetail(store.addNote(incident, actor, note))))
                .orElseGet(() -> ToolResponses.error(objectMapper, "事件不存在: " + incidentId));
    }

    @Tool(returnDirect = true, description = "更新事件时限（分钟），并记录到时间线。")
    public String updateIncidentSla(
            @ToolParam(description = "事件ID") String incidentId,
            @ToolParam(description = "时限分钟数") Integer slaMinutes,
            @ToolParam(description = "操作人") String actor) {
        Duration sla = slaMinutes == null ? null : Duration.ofMinutes(Math.max(1, slaMinutes));
        return store.get(incidentId)
                .map(incident -> ToolResponses.success(objectMapper, toSummary(store.updateSla(incident, sla, actor))))
                .orElseGet(() -> ToolResponses.error(objectMapper, "事件不存在: " + incidentId));
    }

    @Tool(returnDirect = true, description = "事件概览：按状态/严重等级汇总，并计算时限违约数量。")
    public String incidentStats() {
        List<Incident> incidents = store.all();
        Map<String, Long> byStatus = incidents.stream()
                .collect(Collectors.groupingBy(i -> i.getStatus().displayName(), Collectors.counting()));
        Map<String, Long> bySeverity = incidents.stream()
                .collect(Collectors.groupingBy(i -> i.getSeverity().displayName(), Collectors.counting()));
        Instant now = Instant.now();
        long breached = incidents.stream()
                .filter(i -> i.getDueAt() != null)
                .filter(i -> i.getDueAt().isBefore(now))
                .filter(i -> i.getStatus() != IncidentStatus.RESOLVED && i.getStatus() != IncidentStatus.CLOSED)
                .count();

        Map<String, Object> data = new HashMap<>();
        data.put("总数", incidents.size());
        data.put("按状态汇总", byStatus);
        data.put("按严重等级汇总", bySeverity);
        data.put("时限违约数量", breached);
        return ToolResponses.success(objectMapper, data);
    }

    private Map<String, Object> toSummary(Incident incident) {
        Map<String, Object> summary = new HashMap<>();
        summary.put("事件ID", incident.getId());
        summary.put("标题", incident.getTitle());
        summary.put("状态", incident.getStatus().displayName());
        summary.put("严重等级", incident.getSeverity().displayName());
        summary.put("负责人", incident.getAssignee());
        summary.put("标签", incident.getTags());
        summary.put("创建时间", incident.getCreatedAt());
        summary.put("更新时间", incident.getUpdatedAt());
        summary.put("截止时间", incident.getDueAt());
        return summary;
    }

    private Map<String, Object> toDetail(Incident incident) {
        Map<String, Object> detail = toSummary(incident);
        detail.put("描述", incident.getDescription());
        detail.put("创建人", incident.getCreatedBy());
        detail.put("元数据", incident.getMetadata());
        detail.put("时间线", incident.getTimeline());
        return detail;
    }
}
