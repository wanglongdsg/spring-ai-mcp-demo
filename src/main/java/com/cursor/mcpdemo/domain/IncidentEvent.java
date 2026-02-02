package com.cursor.mcpdemo.domain;

import java.time.Instant;

public record IncidentEvent(
        Instant at,
        String type,
        String actor,
        String message
) {
}
