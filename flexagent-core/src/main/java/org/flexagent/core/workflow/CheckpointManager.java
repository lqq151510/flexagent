package org.flexagent.core.workflow;

/**
 * Interface for saving and loading workflow states to support suspending and resuming.
 */
public interface CheckpointManager {
    /**
     * Saves the current workflow state.
     * @param state The state to save.
     */
    void save(WorkflowState state);

    /**
     * Loads a workflow state by ID.
     * @param workflowId The ID of the workflow.
     * @return The saved workflow state, or null if not found.
     */
    WorkflowState load(String workflowId);
}
