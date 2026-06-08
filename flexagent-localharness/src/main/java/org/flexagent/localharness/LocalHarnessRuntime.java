package org.flexagent.localharness;

import org.flexagent.core.model.Step;
import org.flexagent.core.model.ToolResult;
import org.flexagent.core.runtime.AgentConfig;
import org.flexagent.core.runtime.AgentRuntime;
import org.flexagent.localharness.proto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class LocalHarnessRuntime implements org.flexagent.core.runtime.ReactiveAgentRuntime {
    private static final Logger log = LoggerFactory.getLogger(LocalHarnessRuntime.class);

    private HarnessProcessManager processManager;
    private FlexAgentConnection connection;
    private AgentConfig config;

    @Override
    public void initialize(AgentConfig config) throws IOException {
        this.config = config;
        log.info("Initializing LocalHarnessRuntime...");

        this.processManager = new HarnessProcessManager();
        this.processManager.start(config.getBinaryPath(), config.getStorageDirectory());

        this.connection = new FlexAgentConnection(processManager);
        this.connection.connect();

        // Construct HarnessConfig
        HarnessConfig.Builder harnessConfigBuilder = HarnessConfig.newBuilder();

        // 1. Model Configuration
        String modelName = config.getModelName() != null ? config.getModelName() : "gemini-3.5-flash";
        GeminiConfig geminiConfig = GeminiConfig.newBuilder()
                .setModelName(modelName)
                .setThinkingLevel(config.getThinkingLevel() != null ? config.getThinkingLevel() : "high")
                .build();
        harnessConfigBuilder.setGeminiConfig(geminiConfig);

        // 2. System Instructions
        if (config.getSystemInstruction() != null && !config.getSystemInstruction().isEmpty()) {
            SystemInstructions sysInst = SystemInstructions.newBuilder()
                    .setCustom(CustomSystemInstructions.newBuilder()
                            .addPart(CustomSystemInstructions.Part.newBuilder()
                                    .setText(config.getSystemInstruction())
                                    .build())
                            .build())
                    .build();
            harnessConfigBuilder.setSystemInstructions(sysInst);
        }

        // 3. Tools Mapping (from AgentConfig.getTools())
        if (config.getTools() != null) {
            for (org.flexagent.core.model.ToolDefinition td : config.getTools()) {
                Tool pbTool = Tool.newBuilder()
                        .setName(td.name())
                        .setDescription(td.description() != null ? td.description() : "")
                        .setParametersJsonSchema(td.parametersJsonSchema() != null ? td.parametersJsonSchema() : "{}")
                        .build();
                harnessConfigBuilder.addTools(pbTool);
            }
        }

        // 4. Workspace Config
        if (config.getStorageDirectory() != null && !config.getStorageDirectory().isEmpty()) {
            harnessConfigBuilder.addWorkspaces(Workspace.newBuilder()
                    .setFilesystemWorkspace(FilesystemWorkspace.newBuilder()
                            .setDirectory(config.getStorageDirectory())
                            .build())
                    .build());
        }

        // 5. Harness Side Tools Config (Enabled by default)
        HarnessSideTools sideTools = HarnessSideTools.newBuilder()
                .setRunCommand(RunCommandToolConfig.newBuilder().setEnabled(true).build())
                .setFileEdit(FileEditToolConfig.newBuilder().setEnabled(true).build())
                .setViewFile(ViewFileToolConfig.newBuilder().setEnabled(true).build())
                .setWriteToFile(WriteToFileToolConfig.newBuilder().setEnabled(true).build())
                .setListDir(ListDirToolConfig.newBuilder().setEnabled(true).build())
                .setGrepSearch(GrepSearchToolConfig.newBuilder().setEnabled(true).build())
                .setFind(FindToolConfig.newBuilder().setEnabled(true).build())
                .setGenerateImage(GenerateImageToolConfig.newBuilder().setEnabled(true).build())
                .build();
        harnessConfigBuilder.setHarnessSideTools(sideTools);

        // Send conversation initialization event over WebSocket
        this.connection.initialize(harnessConfigBuilder.build());
        log.info("LocalHarnessRuntime initialized successfully.");
    }

    @Override
    public void send(String prompt) throws IOException {
        if (connection == null) {
            throw new IllegalStateException("Runtime not initialized. Call initialize() first.");
        }
        connection.send(prompt);
    }

    @Override
    public Step pollStep(long timeout, TimeUnit unit) throws InterruptedException {
        if (connection == null) {
            throw new IllegalStateException("Runtime not initialized. Call initialize() first.");
        }
        return connection.pollStep(timeout, unit);
    }

    @Override
    public void sendToolResult(ToolResult result) throws IOException {
        if (connection == null) {
            throw new IllegalStateException("Runtime not initialized. Call initialize() first.");
        }
        connection.sendToolResult(result);
    }

    @Override
    public void waitForIdle() {
        if (connection == null) {
            throw new IllegalStateException("Runtime not initialized. Call initialize() first.");
        }
        connection.waitForIdle();
    }

    @Override
    public java.util.Set<org.flexagent.core.model.RuntimeCapability> capabilities() {
        return java.util.Set.of(
                org.flexagent.core.model.RuntimeCapability.STREAMING,
                org.flexagent.core.model.RuntimeCapability.TOOL_CALLING,
                org.flexagent.core.model.RuntimeCapability.THINKING_DELTA,
                org.flexagent.core.model.RuntimeCapability.COMPACTION,
                org.flexagent.core.model.RuntimeCapability.LOCAL_PROCESS
        );
    }

    @Override
    public org.flexagent.core.model.ThinkingMode thinkingMode() {
        return this.config != null ? this.config.getThinkingMode() : org.flexagent.core.model.ThinkingMode.PROVIDER_NATIVE;
    }

    @Override
    public org.flexagent.core.model.ToolCallPolicy toolCallPolicy() {
        return this.config != null ? this.config.getToolCallPolicy() : org.flexagent.core.model.ToolCallPolicy.LENIENT;
    }

    @Override
    public void close() throws Exception {
        if (connection != null) {
            connection.close();
        }
    }

    @Override
    public reactor.core.publisher.Flux<Step> generateStream(String prompt) {
        return reactor.core.publisher.Flux.create(sink -> {
            try {
                send(prompt);
                Thread.ofVirtual().name("localharness-flux-emitter").start(() -> {
                    try {
                        while (!sink.isCancelled()) {
                            Step step = pollStep(100, TimeUnit.MILLISECONDS);
                            if (step != null) {
                                sink.next(step);
                                if (step.status() == org.flexagent.core.model.StepStatus.DONE 
                                    || step.status() == org.flexagent.core.model.StepStatus.ERROR) {
                                    sink.complete();
                                    break;
                                }
                            }
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        sink.complete();
                    } catch (Exception e) {
                        sink.error(e);
                    }
                });
            } catch (Exception e) {
                sink.error(e);
            }
        }, reactor.core.publisher.FluxSink.OverflowStrategy.BUFFER);
    }

    @Override
    public java.util.List<org.flexagent.core.memory.AgentMessage> getHistoryMessages() {
        return java.util.Collections.emptyList();
    }

    @Override
    public void setHistoryMessages(java.util.List<org.flexagent.core.memory.AgentMessage> messages) {
        // No-op for local harness
    }

    @Override
    public void setSessionId(String sessionId) {
        // No-op for local harness
    }
}
