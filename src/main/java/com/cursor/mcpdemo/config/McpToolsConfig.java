package com.cursor.mcpdemo.config;

import com.cursor.mcpdemo.tool.DataRecordToolService;
import com.cursor.mcpdemo.tool.IncidentToolService;
import com.cursor.mcpdemo.tool.RunbookToolService;
import com.cursor.mcpdemo.tool.WorkflowToolService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class McpToolsConfig {

    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return mapper;
    }

    @Bean("incidentTools")
    public ToolCallbackProvider incidentTools(IncidentToolService service) {
        return MethodToolCallbackProvider.builder().toolObjects(service).build();
    }

    @Bean("runbookTools")
    public ToolCallbackProvider runbookTools(RunbookToolService service) {
        return MethodToolCallbackProvider.builder().toolObjects(service).build();
    }

    @Bean("workflowTools")
    public ToolCallbackProvider workflowTools(WorkflowToolService service) {
        return MethodToolCallbackProvider.builder().toolObjects(service).build();
    }

    @Bean("dataRecordTools")
    public ToolCallbackProvider dataRecordTools(DataRecordToolService service) {
        return MethodToolCallbackProvider.builder().toolObjects(service).build();
    }
}
