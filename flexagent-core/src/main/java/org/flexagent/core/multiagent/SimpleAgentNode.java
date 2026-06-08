package org.flexagent.core.multiagent;

import org.flexagent.core.memory.AgentMessage;
import org.flexagent.core.runtime.AgentRuntime;
import org.flexagent.core.strategy.AgentStrategy;

import java.io.IOException;
import java.util.Map;

/**
 * A standard executable implementation of AgentNode that combines an AgentRuntime
 * and an AgentStrategy to run tasks.
 */
public class SimpleAgentNode implements AgentNode {

    private final String name;
    private final String description;
    private final AgentRuntime runtime;
    private final AgentStrategy strategy;

    public SimpleAgentNode(String name, String description, AgentRuntime runtime, AgentStrategy strategy) {
        this.name = name;
        this.description = description;
        this.runtime = runtime;
        this.strategy = strategy;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public AgentMessage execute(String task, Map<String, Object> context) {
        try {
            return strategy.execute(task, runtime, null);
        } catch (IOException e) {
            throw new RuntimeException("Execution failed for agent " + name + ": " + e.getMessage(), e);
        }
    }
}
