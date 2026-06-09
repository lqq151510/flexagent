package org.flexagent.cloud.autoconfigure;

import com.alibaba.cloud.nacos.NacosDiscoveryProperties;
import org.flexagent.cloud.endpoint.FlexAgentCloudEndpoint;
import org.flexagent.cloud.nacos.NacosAgentMetadataRegistrar;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@ConditionalOnClass(NacosDiscoveryProperties.class)
@ConditionalOnProperty(name = "flexagent.cloud.enabled", matchIfMissing = true)
public class FlexAgentCloudAutoConfiguration {

    @Bean
    public NacosAgentMetadataRegistrar nacosAgentMetadataRegistrar(NacosDiscoveryProperties nacosDiscoveryProperties) {
        return new NacosAgentMetadataRegistrar(nacosDiscoveryProperties);
    }

    @Bean
    public FlexAgentCloudEndpoint flexAgentCloudEndpoint(ApplicationContext applicationContext) {
        return new FlexAgentCloudEndpoint(applicationContext);
    }

    @Bean
    @LoadBalanced
    @ConditionalOnMissingBean(name = "loadBalancedWebClientBuilder")
    public WebClient.Builder loadBalancedWebClientBuilder() {
        return WebClient.builder();
    }
}
