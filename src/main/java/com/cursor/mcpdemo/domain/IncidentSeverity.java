package com.cursor.mcpdemo.domain;

import java.util.Locale;

public enum IncidentSeverity {
    CRITICAL,
    HIGH,
    MEDIUM,
    LOW;

    public static IncidentSeverity from(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "严重", "S0", "CRITICAL" -> CRITICAL;
            case "高", "S1", "HIGH" -> HIGH;
            case "中", "S2", "MEDIUM" -> MEDIUM;
            case "低", "S3", "LOW" -> LOW;
            default -> IncidentSeverity.valueOf(normalized);
        };
    }

    public String displayName() {
        return switch (this) {
            case CRITICAL -> "严重";
            case HIGH -> "高";
            case MEDIUM -> "中";
            case LOW -> "低";
        };
    }
}
