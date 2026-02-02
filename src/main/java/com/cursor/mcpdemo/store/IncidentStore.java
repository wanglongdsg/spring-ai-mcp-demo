package com.cursor.mcpdemo.store;

import com.cursor.mcpdemo.domain.Incident;
import com.cursor.mcpdemo.domain.IncidentEvent;
import com.cursor.mcpdemo.domain.IncidentSeverity;
import com.cursor.mcpdemo.domain.IncidentStatus;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Component
public class IncidentStore {
    private final ConcurrentHashMap<String, Incident> incidents = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong(1000);
    private final Clock clock = Clock.systemUTC();

    public Incident create(String title,
                           String description,
                           IncidentSeverity severity,
                           String createdBy,
                           Set<String> tags,
                           Map<String, String> metadata,
                           Duration sla) {
        Instant now = Instant.now(clock);
        String id = "INC-" + sequence.getAndIncrement() + "-" + UUID.randomUUID().toString().substring(0, 6);
        Instant dueAt = sla == null ? null : now.plus(sla);
        Incident incident = new Incident(
                id,
                title,
                description,
                severity,
                IncidentStatus.OPEN,
                createdBy,
                now,
                now,
                dueAt,
                tags,
                metadata
        );
        incident.addEvent(new IncidentEvent(now, "创建", createdBy, "事件已创建"), now);
        incidents.put(id, incident);
        return incident;
    }

    public Optional<Incident> get(String id) {
        return Optional.ofNullable(incidents.get(id));
    }

    public List<Incident> list(IncidentStatus status,
                               IncidentSeverity severity,
                               String assignee,
                               String tag,
                               int limit) {
        return incidents.values().stream()
                .filter(incident -> status == null || incident.getStatus() == status)
                .filter(incident -> severity == null || incident.getSeverity() == severity)
                .filter(incident -> assignee == null || assignee.isBlank() || assignee.equalsIgnoreCase(incident.getAssignee()))
                .filter(incident -> tag == null || tag.isBlank() || incident.getTags().contains(tag))
                .sorted(Comparator.comparing(Incident::getUpdatedAt).reversed())
                .limit(Math.max(1, limit))
                .collect(Collectors.toList());
    }

    public Incident updateStatus(Incident incident, IncidentStatus status, String actor, String note) {
        Instant now = Instant.now(clock);
        incident.setStatus(status, now);
        incident.addEvent(new IncidentEvent(now, "状态", actor, note), now);
        return incident;
    }

    public Incident assign(Incident incident, String assignee, String actor) {
        Instant now = Instant.now(clock);
        incident.setAssignee(assignee, now);
        incident.addEvent(new IncidentEvent(now, "指派", actor, "已指派给 " + assignee), now);
        return incident;
    }

    public Incident addNote(Incident incident, String actor, String note) {
        Instant now = Instant.now(clock);
        incident.addEvent(new IncidentEvent(now, "备注", actor, note), now);
        return incident;
    }

    public Incident updateSla(Incident incident, Duration sla, String actor) {
        Instant now = Instant.now(clock);
        incident.setDueAt(sla == null ? null : now.plus(sla), now);
        incident.addEvent(new IncidentEvent(now, "时限", actor, "已更新时限"), now);
        return incident;
    }

    public List<Incident> all() {
        return new ArrayList<>(incidents.values());
    }
}
