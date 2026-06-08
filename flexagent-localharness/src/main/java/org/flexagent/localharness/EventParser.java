package org.flexagent.localharness;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flexagent.core.model.*;
import org.flexagent.localharness.proto.StepUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.flexagent.core.util.FlexObjectMapper;

public class EventParser {
    private static final Logger log = LoggerFactory.getLogger(EventParser.class);
    private static final ObjectMapper mapper = FlexObjectMapper.getInstance();

    public static Step parseStep(StepUpdate su, org.flexagent.localharness.proto.UsageMetadata pbUsage) {
        UsageMetadata usage = pbUsage != null ? parseUsageMetadata(pbUsage) : null;
        
        List<ToolCall> toolCalls = new ArrayList<>();
        ToolCall activeToolCall = extractToolCallFromStep(su);
        if (activeToolCall != null) {
            toolCalls.add(activeToolCall);
        }

        StepStatus status = mapStatus(su.getState());
        StepSource source = mapSource(su.getSource());
        StepTarget target = mapTarget(su.getTarget());
        StepType type = determineStepType(su);

        Object structuredOutput = null;
        if (su.hasFinish() && su.getFinish().getOutputString() != null) {
            String outputStr = su.getFinish().getOutputString();
            try {
                structuredOutput = mapper.readValue(outputStr, new TypeReference<Map<String, Object>>() {});
            } catch (Exception e) {
                structuredOutput = outputStr;
            }
        }

        boolean isCompleteResponse = (source == StepSource.MODEL 
                && status == StepStatus.DONE 
                && !su.getText().isEmpty() 
                && target == StepTarget.USER);

        return new Step(
                su.getTrajectoryId() + ":" + su.getStepIndex(),
                (int) su.getStepIndex(),
                type,
                source,
                target,
                status,
                su.getText(),
                su.getTextDelta(),
                su.getThinking(),
                su.getThinkingDelta(),
                toolCalls,
                su.getErrorMessage(),
                isCompleteResponse,
                structuredOutput,
                usage
        );
    }

    public static ToolCall parseToolCall(org.flexagent.localharness.proto.ToolCall pbTc) {
        Map<String, Object> arguments = Collections.emptyMap();
        if (pbTc.getArgumentsJson() != null && !pbTc.getArgumentsJson().isEmpty()) {
            try {
                arguments = mapper.readValue(pbTc.getArgumentsJson(), new TypeReference<Map<String, Object>>() {});
            } catch (Exception e) {
                log.warn("Failed to parse arguments JSON for tool call: {}", pbTc.getName(), e);
            }
        }
        return new ToolCall(
                pbTc.getId(),
                pbTc.getName(),
                arguments,
                pbTc.getArgumentsJson(),
                null
        );
    }

    private static ToolCall extractToolCallFromStep(StepUpdate su) {
        if (su.hasRunCommand()) {
            Map<String, Object> args = Map.of(
                    "command_line", su.getRunCommand().getCommandLine(),
                    "working_dir", su.getRunCommand().getWorkingDir()
            );
            return new ToolCall(su.getTrajectoryId() + ":" + su.getStepIndex(), "run_command", args, toJson(args), null);
        } else if (su.hasCreateFile()) {
            Map<String, Object> args = Map.of(
                    "file_path", su.getCreateFile().getFilePath(),
                    "contents", su.getCreateFile().getContents()
            );
            return new ToolCall(su.getTrajectoryId() + ":" + su.getStepIndex(), "create_file", args, toJson(args), su.getCreateFile().getFilePath());
        } else if (su.hasEditFile()) {
            Map<String, Object> args = Map.of("file_path", su.getEditFile().getFilePath());
            return new ToolCall(su.getTrajectoryId() + ":" + su.getStepIndex(), "edit_file", args, toJson(args), su.getEditFile().getFilePath());
        } else if (su.hasViewFile()) {
            Map<String, Object> args = Map.of(
                    "file_path", su.getViewFile().getFilePath(),
                    "start_line", su.getViewFile().getStartLine(),
                    "end_line", su.getViewFile().getEndLine()
            );
            return new ToolCall(su.getTrajectoryId() + ":" + su.getStepIndex(), "view_file", args, toJson(args), su.getViewFile().getFilePath());
        } else if (su.hasListDirectory()) {
            Map<String, Object> args = Map.of("directory_path", su.getListDirectory().getDirectoryPath());
            return new ToolCall(su.getTrajectoryId() + ":" + su.getStepIndex(), "list_directory", args, toJson(args), su.getListDirectory().getDirectoryPath());
        } else if (su.hasFindFile()) {
            Map<String, Object> args = Map.of(
                    "directory_path", su.getFindFile().getDirectoryPath(),
                    "query", su.getFindFile().getQuery()
            );
            return new ToolCall(su.getTrajectoryId() + ":" + su.getStepIndex(), "find_file", args, toJson(args), su.getFindFile().getDirectoryPath());
        } else if (su.hasSearchDirectory()) {
            Map<String, Object> args = Map.of(
                    "directory_path", su.getSearchDirectory().getDirectoryPath(),
                    "query", su.getSearchDirectory().getQuery()
            );
            return new ToolCall(su.getTrajectoryId() + ":" + su.getStepIndex(), "search_directory", args, toJson(args), su.getSearchDirectory().getDirectoryPath());
        } else if (su.hasGenerateImage()) {
            Map<String, Object> args = Map.of(
                    "prompt", su.getGenerateImage().getPrompt(),
                    "image_name", su.getGenerateImage().getImageName()
            );
            return new ToolCall(su.getTrajectoryId() + ":" + su.getStepIndex(), "generate_image", args, toJson(args), null);
        } else if (su.hasFinish()) {
            Map<String, Object> args = Map.of("output_string", su.getFinish().getOutputString());
            return new ToolCall(su.getTrajectoryId() + ":" + su.getStepIndex(), "finish", args, toJson(args), null);
        }
        return null;
    }

    private static StepType determineStepType(StepUpdate su) {
        if (su.hasCompaction()) {
            return StepType.COMPACTION;
        } else if (su.hasFinish()) {
            return StepType.FINISH;
        } else if (su.hasRunCommand() || su.hasCreateFile() || su.hasEditFile() || su.hasViewFile() ||
                su.hasListDirectory() || su.hasFindFile() || su.hasSearchDirectory() || su.hasGenerateImage()) {
            return StepType.TOOL_CALL;
        } else if (!su.getText().isEmpty() || !su.getTextDelta().isEmpty()) {
            return StepType.TEXT_RESPONSE;
        }
        return StepType.UNKNOWN;
    }

    public static UsageMetadata parseUsageMetadata(org.flexagent.localharness.proto.UsageMetadata pb) {
        return new UsageMetadata(
                pb.getPromptTokenCount(),
                pb.getCachedContentTokenCount(),
                pb.getCandidatesTokenCount(),
                pb.getThoughtsTokenCount(),
                pb.getTotalTokenCount()
        );
    }

    private static StepStatus mapStatus(StepUpdate.State pbState) {
        return switch (pbState) {
            case STATE_ACTIVE -> StepStatus.ACTIVE;
            case STATE_DONE -> StepStatus.DONE;
            case STATE_WAITING_FOR_USER -> StepStatus.WAITING_FOR_USER;
            case STATE_ERROR -> StepStatus.ERROR;
            default -> StepStatus.UNKNOWN;
        };
    }

    private static StepSource mapSource(StepUpdate.Source pbSource) {
        return switch (pbSource) {
            case SOURCE_SYSTEM -> StepSource.SYSTEM;
            case SOURCE_USER -> StepSource.USER;
            case SOURCE_MODEL -> StepSource.MODEL;
            default -> StepSource.UNKNOWN;
        };
    }

    private static StepTarget mapTarget(StepUpdate.Target pbTarget) {
        return switch (pbTarget) {
            case TARGET_USER -> StepTarget.USER;
            case TARGET_ENVIRONMENT -> StepTarget.ENVIRONMENT;
            case TARGET_UNSPECIFIED -> StepTarget.UNSPECIFIED;
            default -> StepTarget.UNKNOWN;
        };
    }

    private static String toJson(Object obj) {
        try {
            return mapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }
}
