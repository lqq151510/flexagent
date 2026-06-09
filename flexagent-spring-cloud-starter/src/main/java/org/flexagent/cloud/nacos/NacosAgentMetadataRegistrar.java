package org.flexagent.cloud.nacos;

import com.alibaba.cloud.nacos.NacosDiscoveryProperties;
import org.flexagent.core.multiagent.AgentNode;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;

import java.util.Map;
import java.util.StringJoiner;

/**
 * Automatically registers locally available AgentNodes into Nacos Metadata.
 */
public class NacosAgentMetadataRegistrar implements ApplicationListener<ApplicationReadyEvent> {

    private final NacosDiscoveryProperties nacosDiscoveryProperties;

    public NacosAgentMetadataRegistrar(NacosDiscoveryProperties nacosDiscoveryProperties) {
        this.nacosDiscoveryProperties = nacosDiscoveryProperties;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        ApplicationContext context = event.getApplicationContext();
        
        // Find all local AgentNodes
        Map<String, AgentNode> agentBeans = context.getBeansOfType(AgentNode.class);
        
        if (!agentBeans.isEmpty()) {
            StringJoiner sj = new StringJoiner(",");
            for (AgentNode agent : agentBeans.values()) {
                sj.add(agent.getName());
            }
            
            // Inject into Nacos metadata so other microservices can discover them
            Map<String, String> metadata = nacosDiscoveryProperties.getMetadata();
            metadata.put("flexagent.capabilities", sj.toString());
            
            System.out.println("[FlexAgent Cloud] Registered local agents to Nacos Metadata: " + sj.toString());
        }
    }
}
