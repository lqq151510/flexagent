package org.flexagent.mcp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flexagent.core.model.ToolDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A native, lightweight Model Context Protocol (MCP) Client.
 * Communicates with MCP Servers over standard input/output (stdio) using JSON-RPC 2.0.
 */
public class McpClient implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(McpClient.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private final List<String> command;
    private final Map<Long, CompletableFuture<McpProtocol.Response>> pendingRequests = new ConcurrentHashMap<>();
    private final AtomicLong requestIdGenerator = new AtomicLong(1);

    private Process process;
    private BufferedWriter writer;
    private BufferedReader reader;
    private volatile boolean running = false;
    private Thread stdoutThread;
    private Thread stderrThread;
    private final List<String> lastStderrLines = new CopyOnWriteArrayList<>();

    public McpClient(List<String> command) {
        this.command = new ArrayList<>(command);
    }

    public McpClient(String... command) {
        this.command = Arrays.asList(command);
    }

    /**
     * Starts the MCP server process and establishes stdio connections.
     */
    public synchronized void start() throws IOException {
        if (running) {
            return;
        }

        log.info("Starting MCP Server process: {}", command);
        ProcessBuilder builder = new ProcessBuilder(command);
        this.process = builder.start();

        this.writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
        this.reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
        this.running = true;

        // Start asynchronous stdout reader thread
        this.stdoutThread = Thread.ofVirtual().name("mcp-client-stdout").start(this::readStdoutLoop);

        // Start asynchronous stderr reader thread to prevent process hanging
        this.stderrThread = Thread.ofVirtual().name("mcp-client-stderr").start(this::readStderrLoop);

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
            CompletableFuture<McpProtocol.Response> future = sendRequest("initialize", params);
            McpProtocol.Response response = future.get(10, TimeUnit.SECONDS);

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
            CompletableFuture<McpProtocol.Response> future = sendRequest("tools/list", Collections.emptyMap());
            McpProtocol.Response response = future.get(10, TimeUnit.SECONDS);

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
            CompletableFuture<McpProtocol.Response> future = sendRequest("tools/call", params);
            McpProtocol.Response response = future.get(30, TimeUnit.SECONDS); // Support longer tool execution time

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

    private CompletableFuture<McpProtocol.Response> sendRequest(String method, Object params) throws IOException {
        long id = requestIdGenerator.getAndIncrement();
        McpProtocol.Request request = McpProtocol.Request.create(id, method, params);

        CompletableFuture<McpProtocol.Response> future = new CompletableFuture<>();
        pendingRequests.put(id, future);

        String json = mapper.writeValueAsString(request);
        log.debug("Sending JSON-RPC request: {}", json);

        synchronized (writer) {
            writer.write(json);
            writer.newLine();
            writer.flush();
        }

        return future;
    }

    private void sendNotification(String method, Object params) throws IOException {
        McpProtocol.Notification notification = McpProtocol.Notification.create(method, params);
        String json = mapper.writeValueAsString(notification);
        log.debug("Sending JSON-RPC notification: {}", json);

        synchronized (writer) {
            writer.write(json);
            writer.newLine();
            writer.flush();
        }
    }

    private void readStdoutLoop() {
        try {
            String line;
            while (running && (line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }
                log.debug("Received JSON-RPC line: {}", line);

                try {
                    McpProtocol.Response response = mapper.readValue(line, McpProtocol.Response.class);
                    if (response.id() != null && !response.id().isNull()) {
                        long id;
                        if (response.id().isNumber()) {
                            id = response.id().asLong();
                        } else {
                            try {
                                id = Long.parseLong(response.id().asText());
                            } catch (NumberFormatException e) {
                                log.warn("Received response with non-numeric id: {}", response.id());
                                continue;
                            }
                        }
                        CompletableFuture<McpProtocol.Response> future = pendingRequests.remove(id);
                        if (future != null) {
                            future.complete(response);
                        } else {
                            log.warn("Received response for untracked request id: {}", id);
                        }
                    }
                } catch (Exception e) {
                    log.error("Failed to parse incoming JSON-RPC response line: {}", line, e);
                }
            }
        } catch (IOException e) {
            if (running) {
                log.error("Error reading MCP stdout", e);
            }
        } finally {
            cleanupPendingRequests("Connection closed");
        }
    }

    private void readStderrLoop() {
        try (BufferedReader stderrReader = new BufferedReader(
                new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
            String line;
            while (running && (line = stderrReader.readLine()) != null) {
                log.info("MCP Server [Stderr]: {}", line);
                if (lastStderrLines.size() >= 10) {
                    lastStderrLines.remove(0);
                }
                lastStderrLines.add(line);
            }
        } catch (IOException e) {
            if (running) {
                log.debug("Error reading MCP stderr", e);
            }
        }
    }

    private void cleanupPendingRequests(String reason) {
        int exitCode = -1;
        if (process != null && !process.isAlive()) {
            exitCode = process.exitValue();
        }

        StringBuilder errMsg = new StringBuilder("MCP Client shut down: ").append(reason);
        if (exitCode != -1) {
            errMsg.append(" (Process exited with code ").append(exitCode).append(")");
            if (!lastStderrLines.isEmpty()) {
                errMsg.append(". Last error trace: ").append(String.join(" | ", lastStderrLines));
            }
        }

        String finalError = errMsg.toString();
        for (CompletableFuture<McpProtocol.Response> future : pendingRequests.values()) {
            future.completeExceptionally(new IOException(finalError));
        }
        pendingRequests.clear();
    }

    @Override
    public synchronized void close() throws Exception {
        running = false;
        log.info("Closing MCP Client...");

        if (process != null) {
            process.destroy();
            try {
                if (!process.waitFor(3, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
            }
        }

        if (writer != null) {
            try {
                writer.close();
            } catch (IOException ignored) {}
        }
        if (reader != null) {
            try {
                reader.close();
            } catch (IOException ignored) {}
        }

        cleanupPendingRequests("Client closed");
        log.info("MCP Client successfully closed.");
    }

    // Diagnostic/Testing getter
    public boolean isRunning() {
        return running && process != null && process.isAlive();
    }
}
