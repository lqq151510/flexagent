package org.flexagent.mcp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class StdioMcpTransport implements McpTransport {
    private static final Logger log = LoggerFactory.getLogger(StdioMcpTransport.class);

    private final List<String> command;
    private Process process;
    private BufferedWriter writer;
    private BufferedReader reader;
    private volatile boolean running = false;
    private Thread stdoutThread;
    private Thread stderrThread;
    private final List<String> lastStderrLines = new CopyOnWriteArrayList<>();
    private Consumer<String> responseHandler;

    public StdioMcpTransport(List<String> command) {
        this.command = command;
    }

    @Override
    public void setResponseHandler(Consumer<String> handler) {
        this.responseHandler = handler;
    }

    @Override
    public synchronized void start() throws IOException {
        if (running) {
            return;
        }

        log.info("Starting MCP Server process via stdio: {}", command);
        ProcessBuilder builder = new ProcessBuilder(command);
        this.process = builder.start();

        this.writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
        this.reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
        this.running = true;

        this.stdoutThread = Thread.ofVirtual().name("mcp-transport-stdout").start(this::readStdoutLoop);
        this.stderrThread = Thread.ofVirtual().name("mcp-transport-stderr").start(this::readStderrLoop);
    }

    @Override
    public void sendRequest(String request) throws IOException {
        synchronized (writer) {
            writer.write(request);
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
                if (responseHandler != null) {
                    responseHandler.accept(line);
                }
            }
        } catch (IOException e) {
            if (running) {
                log.error("Error reading MCP stdout", e);
            }
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

    @Override
    public synchronized void close() throws Exception {
        running = false;
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
    }

    public boolean isRunning() {
        return running && process != null && process.isAlive();
    }
}
