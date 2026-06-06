package org.flexagent.console.config;

import org.flexagent.core.model.ToolDefinition;
import org.flexagent.core.runtime.RuntimeTypes;
import org.flexagent.langchain4j.FlexAgentChatModel;
import org.flexagent.mcp.McpClient;
import org.flexagent.mcp.McpToolExecutor;
import org.flexagent.mcp.McpToolScanner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.ai.openai.OpenAiChatModel;

import java.util.List;

@Configuration
public class AgentConfiguration {

    @Bean(destroyMethod = "close")
    public McpClient mcpClient() throws Exception {
        // We'll use npx @modelcontextprotocol/server-everything to provide tools
        McpClient client = new McpClient("npx", "-y", "@modelcontextprotocol/server-everything");
        client.start();
        return client;
    }

    @Bean
    public McpToolExecutor mcpToolExecutor(McpClient mcpClient) throws Exception {
        McpToolScanner scanner = new McpToolScanner(mcpClient);
        List<ToolDefinition> mcpTools = scanner.fetchTools();
        List<String> toolNames = mcpTools.stream().map(ToolDefinition::name).toList();
        return new McpToolExecutor(mcpClient, toolNames);
    }

    @Bean
    public FlexAgentChatModel flexAgentChatModel(OpenAiChatModel springAiModel, McpClient mcpClient, McpToolExecutor mcpToolExecutor) throws Exception {
        McpToolScanner scanner = new McpToolScanner(mcpClient);
        List<ToolDefinition> mcpTools = scanner.fetchTools();

        return FlexAgentChatModel.builder()
                .runtime(RuntimeTypes.SPRING_AI)
                .model(springAiModel)
                .tools(mcpTools.toArray())
                .customToolExecutor(mcpToolExecutor)
                .build();
    }
}
