package org.flexagent.core.strategy;

import org.flexagent.core.memory.AgentMessage;
import org.flexagent.core.runtime.AgentRuntime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.function.Function;

/**
 * The Plan-and-Solve strategy.
 * This strategy first prompts the Agent to generate a step-by-step plan,
 * and then executes the plan using the underlying ReAct strategy loop.
 */
public class PlanAndSolveStrategy implements AgentStrategy {

    private static final Logger log = LoggerFactory.getLogger(PlanAndSolveStrategy.class);
    private final ReActStrategy executorStrategy = new ReActStrategy();

    @Override
    public AgentMessage execute(String prompt, AgentRuntime runtime, Function<org.flexagent.core.model.ToolCall, org.flexagent.core.model.ToolResult> toolExecutor) throws IOException {
        log.info("[PlanAndSolve] Starting Planner Phase");
        
        // 1. Planner Phase
        String plannerPrompt = "Please create a step-by-step plan to solve the following request. " +
                "Do not execute the steps yet, just output the plan starting with [Plan].\n\nRequest: " + prompt;
        
        AgentMessage planMessage = executorStrategy.execute(plannerPrompt, runtime, toolExecutor);
        String plan = planMessage.text();
        
        log.info("[PlanAndSolve] Generated Plan:\n{}", plan);

        // 2. Executor Phase
        log.info("[PlanAndSolve] Starting Executor Phase");
        String executorPrompt = "Here is the plan to solve the user's request:\n" + plan +
                "\n\nPlease execute this plan step-by-step. " +
                "You may use tools as necessary to accomplish each step. " +
                "Once all steps are completed, provide the final answer to the original request: " + prompt;

        return executorStrategy.execute(executorPrompt, runtime, toolExecutor);
    }
}
