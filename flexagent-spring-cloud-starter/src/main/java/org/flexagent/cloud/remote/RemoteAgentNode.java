package org.flexagent.cloud.remote;

import org.flexagent.core.memory.AgentMessage;
import org.flexagent.core.multiagent.AgentNode;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.HashMap;
import java.util.Map;

/**
 * A proxy AgentNode that delegates execution to a remote microservice via Nacos Discovery.
 */
public class RemoteAgentNode implements AgentNode {

    private final String serviceName;
    private final String remoteAgentName;
    private final WebClient webClient;

    public RemoteAgentNode(String serviceName, String remoteAgentName, WebClient.Builder webClientBuilder) {
        this.serviceName = serviceName;
        this.remoteAgentName = remoteAgentName;
        // The WebClientBuilder should be @LoadBalanced in Spring Cloud Context
        this.webClient = webClientBuilder.baseUrl("http://" + serviceName).build();
    }

    @Override
    public String getName() {
        return "RemoteProxy-" + remoteAgentName;
    }

    @Override
    public String getDescription() {
        return "Delegates to agent '" + remoteAgentName + "' on microservice '" + serviceName + "'";
    }

    @Override
    public AgentMessage execute(String task, Map<String, Object> context) {
        Map<String, Object> request = new HashMap<>();
        request.put("agentName", remoteAgentName);
        request.put("task", task);
        request.put("context", context);

        return webClient.post()
                .uri("/api/flexagent/cloud/execute")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(AgentMessage.class)
                .block();
    }
}
