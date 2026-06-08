package org.flexagent.core.workflow;

import org.flexagent.core.multiagent.AgentNode;
import org.flexagent.core.memory.AgentMessage;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * A WorkflowNode that delegates execution to an underlying AgentNode.
 */
public class AgentTaskNode implements WorkflowNode {
    private final String id;
    private final AgentNode agentNode;
    private final List<String> nextNodeIds;
    private final String inputKey;
    private final String outputKey;

    public AgentTaskNode(String id, AgentNode agentNode, List<String> nextNodeIds, String inputKey, String outputKey) {
        this.id = id;
        this.agentNode = agentNode;
        this.nextNodeIds = nextNodeIds != null ? nextNodeIds : Collections.emptyList();
        this.inputKey = inputKey;
        this.outputKey = outputKey;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String execute(WorkflowState state) {
        String taskInput = (String) state.getContextVariables().getOrDefault(inputKey, "");
        AgentMessage reply = agentNode.execute(taskInput, state.getContextVariables());
        String result = reply.text();
        state.setContextVariable(outputKey, result);
        return result;
    }

    @Override
    public List<String> getNextNodeIds() {
        return nextNodeIds;
    }
}
