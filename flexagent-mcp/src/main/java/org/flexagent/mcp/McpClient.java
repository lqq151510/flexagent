package org.flexagent.mcp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flexagent.core.model.ToolDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A native, lightweight Model Context Protocol (MCP) Client.
 * Communicates with MCP Servers using a transport mechanism (e.g. stdio or SSE).
 */
public class McpClient implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(McpClient.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private final McpTransport transport;
    private final Map<Long, CompletableFuture<String>> pendingRequests = new ConcurrentHashMap<>();
    private final AtomicLong requestIdGenerator = new AtomicLong(1);
    private volatile boolean running = false;

    public McpClient(McpTransport transport) {
        this.transport = transport;
        this.transport.setResponseHandler(this::handleResponse);
    }

    public McpClient(List<String> command) {
        this(new StdioMcpTransport(new ArrayList<>(command)));
    }

    public McpClient(String... command) {
        this(new StdioMcpTransport(Arrays.asList(command)));
    }

    /**
     * Starts the MCP server process and establishes connections.
     */
    public synchronized void start() throws IOException {
        if (running) {
            return;
        }

        transport.start();
        this.running = true;

        // Perform MCP protocol handshake
        initializeHandshake();
    }

    private void initializeHandshake() throws IOException {
        McpProtocol.InitializeParams params = new McpProtocol.InitializeParams(
                "2024-11-05",
                new McpProtocol.Capabilities(null, null),
                new McpProtocol.ClientInfo("FlexAgentClient", "1.0.0")
        );

        log.info("Sending MCP initialize request...");
        try {
            CompletableFuture<String> future = sendRequest("initialize", params);
            String responseStr = future.get(30, TimeUnit.SECONDS);
            McpProtocol.Response response = mapper.readValue(responseStr, McpProtocol.Response.class);

            if (response.error() != null) {
                throw new IOException("MCP initialize failed: " + response.error().message());
            }

            McpProtocol.InitializeResult result = mapper.treeToValue(response.result(), McpProtocol.InitializeResult.class);
            log.info("MCP Handshake success. Server Info: {} v{}", 
                    result.serverInfo() != null ? result.serverInfo().name() : "unknown",
                    result.serverInfo() != null ? result.serverInfo().version() : "unknown"
            );

            // Send notifications/initialized (no response required)
            sendNotification("notifications/initialized", Collections.emptyMap());

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("MCP handshake interrupted", e);
        } catch (ExecutionException e) {
            throw new IOException("MCP handshake execution failed", e.getCause());
        } catch (TimeoutException e) {
            throw new IOException("MCP handshake timeout after 10 seconds", e);
        }
    }

    /**
     * Lists tools exposed by the MCP server, converting them to FlexAgent's ToolDefinition.
     */
    public List<ToolDefinition> listTools() throws IOException {
        log.info("Fetching tools from MCP Server...");
        try {
            CompletableFuture<String> future = sendRequest("tools/list", Collections.emptyMap());
            String responseStr = future.get(10, TimeUnit.SECONDS);
            McpProtocol.Response response = mapper.readValue(responseStr, McpProtocol.Response.class);

            if (response.error() != null) {
                throw new IOException("Failed to list tools: " + response.error().message());
            }

            McpProtocol.ToolListResult result = mapper.treeToValue(response.result(), McpProtocol.ToolListResult.class);
            List<ToolDefinition> toolDefinitions = new ArrayList<>();
            if (result.tools() != null) {
                for (McpProtocol.McpTool mcpTool : result.tools()) {
                    String schemaJson = mapper.writeValueAsString(mcpTool.inputSchema());
                    toolDefinitions.add(new ToolDefinition(
                            mcpTool.name(),
                            mcpTool.description() != null ? mcpTool.description() : "",
                            schemaJson
                    ));
                }
            }
            log.info("Successfully fetched {} tools from MCP Server.", toolDefinitions.size());
            return toolDefinitions;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("MCP listTools interrupted", e);
        } catch (ExecutionException e) {
            throw new IOException("MCP listTools execution failed", e.getCause());
        } catch (TimeoutException e) {
            throw new IOException("MCP listTools timeout", e);
        }
    }

    /**
     * Calls a tool exposed by the MCP server.
     */
    public String callTool(String name, Map<String, Object> arguments) throws IOException {
        log.info("Calling MCP tool: {} with arguments: {}", name, arguments);
        McpProtocol.CallToolParams params = new McpProtocol.CallToolParams(name, arguments);

        try {
            CompletableFuture<String> future = sendRequest("tools/call", params);
            String responseStr = future.get(30, TimeUnit.SECONDS); // Support longer tool execution time
            McpProtocol.Response response = mapper.readValue(responseStr, McpProtocol.Response.class);

            if (response.error() != null) {
                throw new IOException("MCP tool call returned error: " + response.error().message());
            }

            McpProtocol.CallToolResult result = mapper.treeToValue(response.result(), McpProtocol.CallToolResult.class);
            if (result.isError() != null && result.isError()) {
                throw new IOException("MCP tool execution failed internally");
            }

            StringBuilder contentBuilder = new StringBuilder();
            if (result.content() != null) {
                for (McpProtocol.McpContent content : result.content()) {
                    if ("text".equals(content.type()) && content.text() != null) {
                        contentBuilder.append(content.text());
                    }
                }
            }
            return contentBuilder.toString();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("MCP callTool interrupted", e);
        } catch (ExecutionException e) {
            throw new IOException("MCP callTool execution failed", e.getCause());
        } catch (TimeoutException e) {
            throw new IOException("MCP callTool timeout", e);
        }
    }

    private CompletableFuture<String> sendRequest(String method, Object params) throws IOException {
        long id = requestIdGenerator.getAndIncrement();
        McpProtocol.Request request = McpProtocol.Request.create(id, method, params);

        CompletableFuture<String> future = new CompletableFuture<>();
        pendingRequests.put(id, future);

        String json = mapper.writeValueAsString(request);
        log.debug("Sending JSON-RPC request: {}", json);

        transport.sendRequest(json);

        return future;
    }

    private void sendNotification(String method, Object params) throws IOException {
        McpProtocol.Notification notification = McpProtocol.Notification.create(method, params);
        String json = mapper.writeValueAsString(notification);
        log.debug("Sending JSON-RPC notification: {}", json);

        transport.sendRequest(json);
    }

    public void handleResponse(String line) {
        log.debug("Received JSON-RPC line: {}", line);

        try {
            JsonNode rootNode = mapper.readTree(line);
            JsonNode idNode = rootNode.get("id");
            
            if (idNode != null && !idNode.isNull()) {
                long id;
                if (idNode.isNumber()) {
                    id = idNode.asLong();
                } else {
                    try {
                        id = Long.parseLong(idNode.asText());
                    } catch (NumberFormatException e) {
                        log.warn("Received response with non-numeric id: {}", idNode);
                        return;
                    }
                }
                CompletableFuture<String> future = pendingRequests.remove(id);
                if (future != null) {
                    future.complete(line);
                } else {
                    log.warn("Received response for untracked request id: {}", id);
                }
            }
        } catch (Exception e) {
            log.error("Failed to parse incoming JSON-RPC response line: {}", line, e);
        }
    }

    private void cleanupPendingRequests(String reason) {
        String finalError = "MCP Client shut down: " + reason;
        for (CompletableFuture<String> future : pendingRequests.values()) {
            future.completeExceptionally(new IOException(finalError));
        }
        pendingRequests.clear();
    }

    @Override
    public synchronized void close() throws Exception {
        running = false;
        log.info("Closing MCP Client...");

        transport.close();

        cleanupPendingRequests("Client closed");
        log.info("MCP Client successfully closed.");
    }

    // Diagnostic/Testing getter
    public boolean isRunning() {
        return running;
    }
}
