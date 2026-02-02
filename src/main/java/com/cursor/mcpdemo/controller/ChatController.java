package com.cursor.mcpdemo.controller;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 接入 DeepSeek 大模型的聊天接口。
 * 用户用自然语言说“往表里插一条数据：标题xxx 内容xxx”，DeepSeek 会调用 MCP 工具 insertData 写入 data_record 表。
 * 响应统一为 JSON：success、reply、message、table（若涉及表操作会带上表名）。
 */
@RestController
@RequestMapping("/api")
public class ChatController {

    private final ChatClient chatClient;

    public ChatController(
            ChatClient.Builder chatClientBuilder,
            @Qualifier("dataRecordTools") ToolCallbackProvider dataRecordTools) {
        this.chatClient = chatClientBuilder
                .defaultToolCallbacks(dataRecordTools)
                .build();
    }



    /**
     * 对话接口：发送一句自然语言，DeepSeek 会按需调用“插入数据 / 查询列表”等工具，并返回回复。
     * 响应为 JSON：{ "success": true, "reply": "...", "message": "ok", "table": "data_record" }（table 在涉及表操作时返回）
     */
    @PostMapping(value = "/chat", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> chat(@RequestBody Map<String, String> body) {
        String message = body != null ? body.get("message") : null;
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("success", true);
        json.put("table", "data_record");
        if (message == null || message.isBlank()) {
            json.put("reply", "");
            json.put("message", "请提供 message 字段，例如：帮我在表里插一条数据，标题是xxx，内容是xxx。");
            return json;
        }
        try {
            String reply = chatClient.prompt()
                    .user(message)
                    .call()
                    .content();
            json.put("reply", reply != null ? reply : "");
            json.put("message", "ok");
        } catch (Exception e) {
            json.put("success", false);
            json.put("reply", "");
            json.put("message", e.getMessage() != null ? e.getMessage() : "调用失败");
        }
        return json;
    }
}
