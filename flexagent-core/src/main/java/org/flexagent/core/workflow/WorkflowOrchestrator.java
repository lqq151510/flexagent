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
    private java.util.concurrent.ExecutorService executorService;

    public WorkflowOrchestrator(CheckpointManager checkpointManager) {
        this.checkpointManager = checkpointManager;
        this.executorService = java.util.concurrent.Executors.newCachedThreadPool();
    }

    public void setExecutorService(java.util.concurrent.ExecutorService executorService) {
        this.executorService = executorService;
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
        
        state.setExecutorService(this.executorService);
        
        if (checkpointManager != null) {
            checkpointManager.save(state);
        }

        while (!state.isFinished() && state.getCurrentNodeId() != null) {
            String currentNodeId = state.getCurrentNodeId();
            WorkflowNode node = nodes.get(currentNodeId);
            if (node == null) {
                throw new IllegalStateException("Node not found: " + currentNodeId);
            }

            // Execute the node
            try {
                String result = node.execute(state);

                // Determine next node
                List<String> nextIds = node.getNextNodeIds();
                if (nextIds == null || nextIds.isEmpty()) {
                    state.setFinished(true);
                    state.setFinalResult(result);
                    state.setCurrentNodeId(null);
                } else {
                    state.setCurrentNodeId(nextIds.get(0));
                }

                // Save checkpoint after each node execution
                if (checkpointManager != null) {
                    checkpointManager.save(state);
                }
            } catch (Exception e) {
                // Save state even on crash so failover node can inspect or retry if policy allows
                if (checkpointManager != null) {
                    checkpointManager.save(state);
                }
                throw e;
            }
        }

        return state.getFinalResult();
    }
}
