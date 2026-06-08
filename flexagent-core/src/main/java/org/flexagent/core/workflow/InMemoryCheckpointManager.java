package org.flexagent.core.workflow;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A simple in-memory implementation of CheckpointManager.
 */
public class InMemoryCheckpointManager implements CheckpointManager {
    private final Map<String, WorkflowState> store = new ConcurrentHashMap<>();

    @Override
    public void save(WorkflowState state) {
        store.put(state.getWorkflowId(), state);
    }

    @Override
    public WorkflowState load(String workflowId) {
        return store.get(workflowId);
    }
}
