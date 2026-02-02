package com.cursor.mcpdemo.domain;

import java.util.Locale;

public enum IncidentStatus {
    OPEN,
    ACKNOWLEDGED,
    MITIGATING,
    RESOLVED,
    CLOSED;

    public static IncidentStatus from(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "未处理", "OPEN" -> OPEN;
            case "已确认", "ACKNOWLEDGED" -> ACKNOWLEDGED;
            case "处理中", "MITIGATING" -> MITIGATING;
            case "已解决", "RESOLVED" -> RESOLVED;
            case "已关闭", "CLOSED" -> CLOSED;
            default -> IncidentStatus.valueOf(normalized);
        };
    }

    public String displayName() {
        return switch (this) {
            case OPEN -> "未处理";
            case ACKNOWLEDGED -> "已确认";
            case MITIGATING -> "处理中";
            case RESOLVED -> "已解决";
            case CLOSED -> "已关闭";
        };
    }
}
