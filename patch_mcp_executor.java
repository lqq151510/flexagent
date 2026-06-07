package org.flexagent.mcp;

import org.flexagent.core.model.ToolCall;
import org.flexagent.core.model.ToolResult;
import org.flexagent.core.tool.CustomToolExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Executes MCP tools using the underlying McpClient.
 * Bridges FlexAgent custom tool execution to the MCP stdio protocol.
 */
public class McpToolExecutor implements CustomToolExecutor {
    private static final Logger log = LoggerFactory.getLogger(McpToolExecutor.class);

    private final McpClient mcpClient;
    private Set<String> supportedTools;

    public McpToolExecutor(McpClient mcpClient, Collection<String> toolNames) {
        this.mcpClient = mcpClient;
        this.supportedTools = new HashSet<>(toolNames);
    }
    
    public void setSupportedTools(Collection<String> toolNames) {
        this.supportedTools = new HashSet<>(toolNames);
    }

    public void addSupportedTool(String toolName) {
        this.supportedTools.add(toolName);
    }

    public void removeSupportedTool(String toolName) {
        this.supportedTools.remove(toolName);
    }

    @Override
    public boolean supports(String toolName) {
        return supportedTools.contains(toolName);
    }

    @Override
    public ToolResult execute(ToolCall toolCall) {
        log.info("McpToolExecutor dispatching call: {} with args: {}", toolCall.name(), toolCall.argumentsJson());
        try {
            if (!mcpClient.isRunning()) {
                mcpClient.start();
            }
            String result = mcpClient.callTool(toolCall.name(), toolCall.arguments());
            return new ToolResult(toolCall.id(), toolCall.name(), result, null);
        } catch (Exception e) {
            log.error("Failed to execute MCP tool: {}", toolCall.name(), e);
            return new ToolResult(toolCall.id(), toolCall.name(), null, "MCP execution error: " + e.getMessage());
        }
    }
}
