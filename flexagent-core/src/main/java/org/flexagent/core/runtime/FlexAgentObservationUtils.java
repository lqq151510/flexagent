package org.flexagent.core.runtime;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.flexagent.core.memory.AgentMessage;

import java.util.function.Supplier;

/**
 * Utility class for Micrometer Observability metrics in FlexAgent.
 */
public class FlexAgentObservationUtils {

    private static ObservationRegistry registry = ObservationRegistry.NOOP;

    public static void setRegistry(ObservationRegistry observationRegistry) {
        if (observationRegistry != null) {
            registry = observationRegistry;
        }
    }

    public static ObservationRegistry getRegistry() {
        return registry;
    }

    /**
     * Measure the execution time of a tool invocation.
     */
    public static <T> T observeToolInvoke(String toolName, Supplier<T> toolExecution) {
        return Observation.createNotStarted("flexagent.tool.invoke.timer", registry)
                .lowCardinalityKeyValue("toolName", toolName)
                .observe(toolExecution);
    }

    /**
     * Record a memory hit metric.
     */
    public static void recordMemoryHit(String sessionId, boolean isHit) {
        Observation.createNotStarted("flexagent.memory.hit", registry)
                .lowCardinalityKeyValue("sessionId", sessionId)
                .lowCardinalityKeyValue("hit", String.valueOf(isHit))
                .start()
                .stop();
    }

    /**
     * Record LLM token usage.
     */
    public static void recordTokenUsage(String provider, int inputTokens, int outputTokens) {
        Observation.createNotStarted("flexagent.llm.token.usage", registry)
                .lowCardinalityKeyValue("provider", provider)
                .highCardinalityKeyValue("inputTokens", String.valueOf(inputTokens))
                .highCardinalityKeyValue("outputTokens", String.valueOf(outputTokens))
                .start()
                .stop();
    }
}
