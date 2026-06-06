package org.flexagent.mcp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;

/**
 * Defines JSON-RPC 2.0 entities for the Model Context Protocol (MCP).
 * Utilizing Java 21 records for clean, lightweight domain modeling.
 */
public final class McpProtocol {

    private McpProtocol() {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Request(
            String jsonrpc,
            Object id, // Can be String or Number
            String method,
            Object params
    ) {
        public static Request create(Object id, String method, Object params) {
            return new Request("2.0", id, method, params);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Notification(
            String jsonrpc,
            String method,
            Object params
    ) {
        public static Notification create(String method, Object params) {
            return new Notification("2.0", method, params);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Response(
            String jsonrpc,
            JsonNode id,
            JsonNode result,
            ErrorDetail error
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ErrorDetail(
            int code,
            String message,
            JsonNode data
    ) {}

    // Initialize Request Parameters
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record InitializeParams(
            String protocolVersion,
            Capabilities capabilities,
            ClientInfo clientInfo
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Capabilities(
            Map<String, Object> roots,
            Map<String, Object> sampling
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ClientInfo(
            String name,
            String version
    ) {}

    // Initialize Response Result
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record InitializeResult(
            String protocolVersion,
            JsonNode capabilities,
            ServerInfo serverInfo
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ServerInfo(
            String name,
            String version
    ) {}

    // List Tools Result
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ToolListResult(
            List<McpTool> tools,
            String nextCursor
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record McpTool(
            String name,
            String description,
            JsonNode inputSchema
    ) {}

    // Call Tool Params and Result
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CallToolParams(
            String name,
            Map<String, Object> arguments
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CallToolResult(
            List<McpContent> content,
            Boolean isError
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record McpContent(
            String type, // usually "text"
            String text,
            JsonNode json,
            JsonNode image
    ) {}
}
