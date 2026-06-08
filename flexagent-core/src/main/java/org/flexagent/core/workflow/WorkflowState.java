package org.flexagent.core.workflow;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents the state of a running workflow.
 */
public class WorkflowState implements Serializable {
    private final String workflowId;
    private final Map<String, Object> contextVariables = new HashMap<>();
    private String currentNodeId;
    private boolean isFinished;
    private String finalResult;

    public WorkflowState(String workflowId) {
        this.workflowId = workflowId;
        this.isFinished = false;
    }

    public String getWorkflowId() { return workflowId; }

    public Map<String, Object> getContextVariables() { return contextVariables; }
    
    public void setContextVariable(String key, Object value) {
        contextVariables.put(key, value);
    }

    public String getCurrentNodeId() { return currentNodeId; }
    public void setCurrentNodeId(String currentNodeId) { this.currentNodeId = currentNodeId; }

    public boolean isFinished() { return isFinished; }
    public void setFinished(boolean finished) { isFinished = finished; }

    public String getFinalResult() { return finalResult; }
    public void setFinalResult(String finalResult) { this.finalResult = finalResult; }
}
