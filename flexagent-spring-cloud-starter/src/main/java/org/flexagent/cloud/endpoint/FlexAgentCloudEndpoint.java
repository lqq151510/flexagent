package org.flexagent.cloud.endpoint;

import org.flexagent.core.memory.AgentMessage;
import org.flexagent.core.multiagent.AgentNode;
import org.springframework.context.ApplicationContext;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Exposes local AgentNodes to be called by other microservices over HTTP.
 */
@RestController
@RequestMapping("/api/flexagent/cloud")
public class FlexAgentCloudEndpoint {

    private final ApplicationContext applicationContext;

    public FlexAgentCloudEndpoint(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    public static class RemoteExecuteRequest {
        private String agentName;
        private String task;
        private Map<String, Object> context;

        public String getAgentName() { return agentName; }
        public void setAgentName(String agentName) { this.agentName = agentName; }
        public String getTask() { return task; }
        public void setTask(String task) { this.task = task; }
        public Map<String, Object> getContext() { return context; }
        public void setContext(Map<String, Object> context) { this.context = context; }
    }

    @PostMapping("/execute")
    public AgentMessage executeRemote(@RequestBody RemoteExecuteRequest request) {
        // Find the requested agent in the local context
        Map<String, AgentNode> beans = applicationContext.getBeansOfType(AgentNode.class);
        
        AgentNode targetAgent = null;
        for (AgentNode agent : beans.values()) {
            if (agent.getName().equals(request.getAgentName())) {
                targetAgent = agent;
                break;
            }
        }
        
        if (targetAgent == null) {
            throw new IllegalArgumentException("Agent not found locally: " + request.getAgentName());
        }

        // Execute locally and return the result
        return targetAgent.execute(request.getTask(), request.getContext());
    }
}
