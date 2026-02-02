package com.cursor.mcpdemo.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

public class Incident {
    private final String id;
    private String title;
    private String description;
    private IncidentSeverity severity;
    private IncidentStatus status;
    private String assignee;
    private String createdBy;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant dueAt;
    private final Set<String> tags;
    private final Map<String, String> metadata;
    private final List<IncidentEvent> timeline;

    public Incident(String id,
                    String title,
                    String description,
                    IncidentSeverity severity,
                    IncidentStatus status,
                    String createdBy,
                    Instant createdAt,
                    Instant updatedAt,
                    Instant dueAt,
                    Set<String> tags,
                    Map<String, String> metadata) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.severity = severity;
        this.status = status;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.dueAt = dueAt;
        this.tags = tags == null ? new LinkedHashSet<>() : new LinkedHashSet<>(tags);
        this.metadata = metadata == null ? Map.of() : metadata;
        this.timeline = new CopyOnWriteArrayList<>();
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public IncidentSeverity getSeverity() {
        return severity;
    }

    public IncidentStatus getStatus() {
        return status;
    }

    public String getAssignee() {
        return assignee;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getDueAt() {
        return dueAt;
    }

    public Set<String> getTags() {
        return Collections.unmodifiableSet(tags);
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public List<IncidentEvent> getTimeline() {
        return Collections.unmodifiableList(new ArrayList<>(timeline));
    }

    public void setTitle(String title, Instant now) {
        this.title = title;
        touch(now);
    }

    public void setDescription(String description, Instant now) {
        this.description = description;
        touch(now);
    }

    public void setSeverity(IncidentSeverity severity, Instant now) {
        this.severity = severity;
        touch(now);
    }

    public void setStatus(IncidentStatus status, Instant now) {
        this.status = status;
        touch(now);
    }

    public void setAssignee(String assignee, Instant now) {
        this.assignee = assignee;
        touch(now);
    }

    public void setDueAt(Instant dueAt, Instant now) {
        this.dueAt = dueAt;
        touch(now);
    }

    public void addTag(String tag, Instant now) {
        if (tag != null && !tag.isBlank()) {
            tags.add(tag.trim());
            touch(now);
        }
    }

    public void addEvent(IncidentEvent event, Instant now) {
        if (event != null) {
            timeline.add(event);
            touch(now);
        }
    }

    private void touch(Instant now) {
        this.updatedAt = now;
    }
}
