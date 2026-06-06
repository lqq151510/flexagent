package org.flexagent.mcp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.function.Consumer;

public class SseMcpTransport implements McpTransport {
    private static final Logger log = LoggerFactory.getLogger(SseMcpTransport.class);

    private final String url;
    private final HttpClient httpClient;
    private Consumer<String> responseHandler;
    private volatile boolean running = false;

    public SseMcpTransport(String url) {
        this.url = url;
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .build();
    }

    @Override
    public void setResponseHandler(Consumer<String> handler) {
        this.responseHandler = handler;
    }

    @Override
    public void start() throws IOException {
        if (running) {
            return;
        }
        running = true;
        log.info("Starting SSE MCP Transport at {}", url);
        // Skeletal implementation: In a real SSE client, we would use HttpResponse.BodyHandlers.ofLines()
        // and process the event stream asynchronously.
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Accept", "text/event-stream")
                .GET()
                .build();
        
        Thread.ofVirtual().name("mcp-transport-sse").start(() -> {
            try {
                httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofLines())
                        .thenAccept(response -> {
                            response.body().forEach(line -> {
                                if (!running) return;
                                if (line.startsWith("data: ")) {
                                    String json = line.substring(6).trim();
                                    if (!json.isEmpty() && responseHandler != null) {
                                        responseHandler.accept(json);
                                    }
                                }
                            });
                        }).join();
            } catch (Exception e) {
                if (running) {
                    log.error("SSE connection error", e);
                }
            }
        });
    }

    @Override
    public void sendRequest(String request) throws IOException {
        log.debug("SSE sendRequest (skeletal): {}", request);
        HttpRequest postRequest = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(request))
                .build();
        
        try {
            httpClient.send(postRequest, HttpResponse.BodyHandlers.discarding());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while sending request over SSE", e);
        }
    }

    @Override
    public void close() throws Exception {
        running = false;
        log.info("Closing SSE MCP Transport");
    }
}
