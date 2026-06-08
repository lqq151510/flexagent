package org.flexagent.core.workflow;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Executes a DAG workflow of Agent nodes.
 */
public class WorkflowOrchestrator {
    private final Map<String, WorkflowNode> nodes = new HashMap<>();
    private final CheckpointManager checkpointManager;

    public WorkflowOrchestrator(CheckpointManager checkpointManager) {
        this.checkpointManager = checkpointManager;
    }

    public void addNode(WorkflowNode node) {
        nodes.put(node.getId(), node);
    }

    /**
     * Starts or resumes a workflow.
     */
    public String run(String workflowId, String startNodeId, Map<String, Object> initialContext) {
        WorkflowState state = null;
        if (checkpointManager != null) {
            state = checkpointManager.load(workflowId);
        }

        if (state == null) {
            state = new WorkflowState(workflowId);
            state.setCurrentNodeId(startNodeId);
            if (initialContext != null) {
                state.getContextVariables().putAll(initialContext);
            }
        }

        while (!state.isFinished() && state.getCurrentNodeId() != null) {
            String currentNodeId = state.getCurrentNodeId();
            WorkflowNode node = nodes.get(currentNodeId);
            if (node == null) {
                throw new IllegalStateException("Node not found: " + currentNodeId);
            }

            // Execute the node
            String result = node.execute(state);

            // Determine next node (For simplicity in this engine, we assume sequential flow or parallel divergence that converges back if specified)
            List<String> nextIds = node.getNextNodeIds();
            if (nextIds == null || nextIds.isEmpty()) {
                state.setFinished(true);
                state.setFinalResult(result);
                state.setCurrentNodeId(null);
            } else {
                // If there's multiple next IDs from a non-parallel node, it could imply a split, 
                // but our simple Orchestrator moves sequentially to the first configured next node.
                // Complex splits should use ParallelNode which internalizes the divergence.
                state.setCurrentNodeId(nextIds.get(0));
            }

            // Save checkpoint after each node execution
            if (checkpointManager != null) {
                checkpointManager.save(state);
            }
        }

        return state.getFinalResult();
    }
}
