package org.flexagent.localharness;

import org.flexagent.localharness.proto.InputConfig;
import org.flexagent.localharness.proto.OutputConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.concurrent.TimeUnit;

public class HarnessProcessManager implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(HarnessProcessManager.class);

    private Process process;
    private int port;
    private String apiKey;

    public void start(String binaryPath, String storageDirectory) throws IOException {
        log.info("Starting localharness process from: {}, storage: {}", binaryPath, storageDirectory);

        ProcessBuilder pb = new ProcessBuilder(binaryPath);
        this.process = pb.start();

        // Spawn a virtual thread to drain and log process stderr
        Thread.ofVirtual().name("harness-stderr-drain").start(this::drainStderr);

        try {
            handshake(storageDirectory);
        } catch (Exception e) {
            log.error("Failed to complete handshake with harness process", e);
            stop();
            throw new IOException("Harness handshake failed", e);
        }
    }

    private void handshake(String storageDirectory) throws IOException {
        OutputStream stdin = process.getOutputStream();
        InputStream stdout = process.getInputStream();

        // 1. Serialize InputConfig
        InputConfig inputConfig = InputConfig.newBuilder()
                .setStorageDirectory(storageDirectory != null ? storageDirectory : "")
                .build();
        byte[] serialized = inputConfig.toByteArray();

        // 2. Write 4-byte little-endian length + serialized bytes
        byte[] lengthBytes = intToLittleEndian(serialized.length);
        stdin.write(lengthBytes);
        stdin.write(serialized);
        stdin.flush();

        // 3. Read 4-byte little-endian response length
        byte[] respLenBytes = stdout.readNBytes(4);
        if (respLenBytes.length < 4) {
            throw new IOException("EOF or incomplete read when waiting for OutputConfig length prefix");
        }
        int respLen = littleEndianToInt(respLenBytes);

        // 4. Read serialized OutputConfig bytes
        byte[] respBytes = stdout.readNBytes(respLen);
        if (respBytes.length < respLen) {
            throw new IOException("EOF or incomplete read when waiting for OutputConfig payload");
        }

        // 5. Parse OutputConfig
        OutputConfig outputConfig = OutputConfig.parseFrom(respBytes);
        this.port = outputConfig.getPort();
        this.apiKey = outputConfig.getApiKey();

        log.info("Handshake successful. Ephemeral WebSocket Port: {}, API Key: [REDACTED]", port);
    }

    private void drainStderr() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                log.info("[harness stderr] {}", line);
            }
        } catch (IOException e) {
            // Stream closed when process terminated
        }
    }

    public int getPort() {
        return port;
    }

    public String getApiKey() {
        return apiKey;
    }

    public boolean isAlive() {
        return process != null && process.isAlive();
    }

    public void stop() {
        if (process == null) {
            return;
        }
        log.info("Stopping harness process...");
        try {
            // Graceful shutdown by closing stdin to trigger Go EOF
            process.getOutputStream().close();
            if (process.waitFor(5, TimeUnit.SECONDS)) {
                log.info("Harness process exited gracefully.");
                return;
            }
        } catch (Exception e) {
            log.warn("Error waiting for harness process to exit gracefully", e);
        }

        // Force terminate
        log.warn("Escalating: terminating harness process...");
        process.destroy();
        try {
            if (process.waitFor(1, TimeUnit.SECONDS)) {
                return;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        log.warn("Escalating: killing harness process...");
        process.destroyForcibly();
    }

    @Override
    public void close() {
        stop();
    }

    private static byte[] intToLittleEndian(int val) {
        return new byte[]{
                (byte) (val & 0xFF),
                (byte) ((val >> 8) & 0xFF),
                (byte) ((val >> 16) & 0xFF),
                (byte) ((val >> 24) & 0xFF)
        };
    }

    private static int littleEndianToInt(byte[] bytes) {
        return (bytes[0] & 0xFF) |
               ((bytes[1] & 0xFF) << 8) |
               ((bytes[2] & 0xFF) << 16) |
               ((bytes[3] & 0xFF) << 24);
    }
}
