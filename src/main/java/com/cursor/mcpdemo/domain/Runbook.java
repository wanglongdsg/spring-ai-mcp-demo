package com.cursor.mcpdemo.domain;

import java.util.List;
import java.util.Set;

public record Runbook(
        String id,
        String title,
        String description,
        List<String> steps,
        Set<String> tags
) {
}
