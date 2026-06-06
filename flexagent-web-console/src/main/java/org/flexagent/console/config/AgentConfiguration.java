package org.flexagent.console.config;

import org.flexagent.core.Agent;
import org.flexagent.core.AgentConfig;
import org.flexagent.core.runtime.AgentRuntime;
import org.flexagent.core.tool.ToolManager;
import org.flexagent.mcp.McpClient;
import org.flexagent.mcp.McpToolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
    public Agent webConsoleAgent(AgentRuntime runtime, McpClient mcpClient) throws Exception {
        ToolManager toolManager = new ToolManager();
        
        // Expose MCP tools to the agent
        List<String> toolNames = mcpClient.listTools().stream()
                .map(t -> t.name())
                .toList();
        
        if (!toolNames.isEmpty()) {
            McpToolExecutor mcpExecutor = new McpToolExecutor(mcpClient, toolNames);
            toolManager.register(mcpExecutor);
        }

        AgentConfig config = new AgentConfig();
        config.setSessionId("web-console-session");
        
        return new Agent(runtime, toolManager, config);
    }
}
