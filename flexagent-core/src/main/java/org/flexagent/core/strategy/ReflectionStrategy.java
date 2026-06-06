package org.flexagent.core.strategy;

import org.flexagent.core.memory.AgentMessage;
import org.flexagent.core.runtime.AgentRuntime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.function.Function;

/**
 * The Reflection Strategy.
 * Generates an initial answer, then critically reflects upon it and generates a refined, improved answer.
 */
public class ReflectionStrategy implements AgentStrategy {
    private static final Logger log = LoggerFactory.getLogger(ReflectionStrategy.class);
    private final AgentStrategy executorStrategy;

    public ReflectionStrategy() {
        this(new ReActStrategy());
    }

    public ReflectionStrategy(AgentStrategy executorStrategy) {
        this.executorStrategy = executorStrategy;
    }

    @Override
    public AgentMessage execute(String prompt, AgentRuntime runtime, Function<org.flexagent.core.model.ToolCall, org.flexagent.core.model.ToolResult> toolExecutor) throws IOException {
        log.info("[Reflection] Generation Phase");
        
        // 1. Generation Phase
        AgentMessage initialMessage = executorStrategy.execute(prompt, runtime, toolExecutor);
        String initialAnswer = initialMessage.text();
        log.info("[Reflection] Initial Answer:\n{}", initialAnswer);
        
        // 2. Reflection and Refinement Phase
        log.info("[Reflection] Refinement Phase");
        String reflectionPrompt = "Please review your previous answer to the following request and critically reflect on it.\n" +
                "Identify any errors, missing information, or logical flaws.\n\n" +
                "Original Request: " + prompt + "\n\n" +
                "Your Previous Answer: " + initialAnswer + "\n\n" +
                "Provide a refined, complete and final answer based on your reflection. " +
                "You may use tools if you need to gather additional information to correct the answer.";
                
        return executorStrategy.execute(reflectionPrompt, runtime, toolExecutor);
    }
}
