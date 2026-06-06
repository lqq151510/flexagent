package org.flexagent.mcp;

import java.io.IOException;
import java.util.function.Consumer;

public interface McpTransport extends AutoCloseable {
    void start() throws IOException;
    void sendRequest(String request) throws IOException;
    void setResponseHandler(Consumer<String> handler);
    @Override
    void close() throws Exception;
}
