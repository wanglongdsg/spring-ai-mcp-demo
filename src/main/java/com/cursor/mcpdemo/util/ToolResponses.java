package com.cursor.mcpdemo.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ToolResponses {

    private static final String TIMESTAMP = "timestamp";

    private ToolResponses() {
    }

    /** 成功响应（通用，无表名） */
    public static String success(ObjectMapper mapper, Object data) {
        return write(mapper, Map.of(
                "success", true,
                TIMESTAMP, Instant.now().toString(),
                "data", data
        ));
    }

    /** 成功响应（带表名，便于明确插入/查询的是哪张表） */
    public static String success(ObjectMapper mapper, String table, Object data) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("success", true);
        payload.put("table", table);
        payload.put(TIMESTAMP, Instant.now().toString());
        payload.put("data", data);
        return write(mapper, payload);
    }

    /** 错误响应，标准 JSON */
    public static String error(ObjectMapper mapper, String message) {
        return write(mapper, Map.of(
                "success", false,
                TIMESTAMP, Instant.now().toString(),
                "message", message != null ? message : "未知错误"
        ));
    }

    private static String write(ObjectMapper mapper, Map<String, Object> payload) {
        try {
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            return "{\"success\":false,\"message\":\"序列化失败\"}";
        }
    }
}
