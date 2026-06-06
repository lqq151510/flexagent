package org.flexagent.mcp;

import org.flexagent.core.model.ToolDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Scans tools from a Model Context Protocol (MCP) Server using a started McpClient.
 */
public class McpToolScanner {
    private static final Logger log = LoggerFactory.getLogger(McpToolScanner.class);

    private final McpClient mcpClient;

    public McpToolScanner(McpClient mcpClient) {
        this.mcpClient = mcpClient;
    }

    /**
     * Fetches tools from the active MCP server and converts them into FlexAgent ToolDefinitions.
     * @return List of ToolDefinition
     */
    public List<ToolDefinition> fetchTools() {
        log.info("Fetching tools from real MCP Client...");
        if (mcpClient == null) {
            log.warn("McpClient is not initialized.");
            return new ArrayList<>();
        }
        try {
            if (!mcpClient.isRunning()) {
                mcpClient.start();
            }
            return mcpClient.listTools();
        } catch (Exception e) {
            log.error("Failed to fetch tools from MCP Server", e);
            throw new RuntimeException("Failed to scan MCP tools", e);
        }
    }
}
