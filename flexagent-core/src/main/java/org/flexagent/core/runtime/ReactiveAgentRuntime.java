package org.flexagent.core.runtime;

import org.flexagent.core.model.Step;
import reactor.core.publisher.Flux;

/**
 * A reactive extension of the AgentRuntime that allows streaming the execution steps
 * non-blockingly using Project Reactor.
 */
public interface ReactiveAgentRuntime extends AgentRuntime {
    
    /**
     * Non-blockingly sends a prompt and streams back the execution steps.
     * @param prompt The user's input prompt
     * @return A Flux of execution steps representing the agent's internal state machine
     */
    Flux<Step> generateStream(String prompt);
}
