package com.cursor.mcpdemo.tool;

import com.cursor.mcpdemo.domain.DataRecord;
import com.cursor.mcpdemo.service.DataRecordService;
import com.cursor.mcpdemo.util.ToolResponses;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * MCP 工具：往 data_record 表中插入数据、查询列表。
 * 供 MCP 客户端（如 Cursor）或本服务的 DeepSeek 聊天接口调用。
 * 响应均为 JSON 字符串，且包含 table 字段标明表名。
 */
@Service
public class DataRecordToolService {

    public static final String TABLE_NAME = "data_record";

    private final DataRecordService dataRecordService;
    private final ObjectMapper objectMapper;

    public DataRecordToolService(DataRecordService dataRecordService, ObjectMapper objectMapper) {
        this.dataRecordService = dataRecordService;
        this.objectMapper = objectMapper;
    }

    @Tool(returnDirect = true, description = "往 data_record 表中插入一条数据。用于保存用户要记录的内容，如笔记、待办、摘要等。")
    public String insertData(
            @ToolParam(description = "标题，必填") String title,
            @ToolParam(description = "正文内容，可选") String content) {
        if (title == null || title.isBlank()) {
            return ToolResponses.error(objectMapper, "标题不能为空");
        }
        DataRecord record = dataRecordService.insert(title.trim(), content != null ? content.trim() : "");
        Map<String, Object> data = Map.of(
                "id", record.getId(),
                "title", record.getTitle(),
                "content", record.getContent() != null ? record.getContent() : "",
                "createdAt", record.getCreatedAt().toString()
        );
        return ToolResponses.success(objectMapper, TABLE_NAME, data);
    }

    @Tool(returnDirect = true, description = "查询 data_record 表中最近插入的数据列表，按创建时间倒序。")
    public String listData(
            @ToolParam(description = "最多返回条数，默认 20") Integer limit) {
        int max = limit != null && limit > 0 ? Math.min(limit, 100) : 20;
        List<DataRecord> records = dataRecordService.list(max);
        List<Map<String, Object>> list = records.stream()
                .map(r -> Map.<String, Object>of(
                        "id", r.getId(),
                        "title", r.getTitle(),
                        "content", r.getContent() != null ? r.getContent() : "",
                        "createdAt", r.getCreatedAt().toString()
                ))
                .collect(Collectors.toList());
        return ToolResponses.success(objectMapper, TABLE_NAME, Map.of("total", list.size(), "items", list));
    }
}
