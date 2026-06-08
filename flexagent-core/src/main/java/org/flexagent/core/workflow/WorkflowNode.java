package org.flexagent.core.workflow;

import java.util.List;

/**
 * Represents a single node in the DAG workflow.
 */
public interface WorkflowNode {
    /**
     * @return The unique ID of this node.
     */
    String getId();

    /**
     * Executes the logic of this node.
     * @param state The current global state of the workflow.
     * @return The output of this node.
     */
    String execute(WorkflowState state);

    /**
     * @return The IDs of the nodes that should be executed after this one. 
     * Can be empty if this is a terminal node.
     */
    List<String> getNextNodeIds();
}
