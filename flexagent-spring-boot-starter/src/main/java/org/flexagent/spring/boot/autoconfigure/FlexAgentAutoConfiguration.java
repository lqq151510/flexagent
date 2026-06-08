package org.flexagent.spring.boot.autoconfigure;

import org.flexagent.langchain4j.FlexAgentChatModel;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.util.StringUtils;
import java.util.ArrayList;
import java.util.List;

@AutoConfiguration
@ConditionalOnClass(FlexAgentChatModel.class)
@EnableConfigurationProperties(FlexAgentProperties.class)
public class FlexAgentAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public org.flexagent.core.memory.AgentMemory agentMemory(FlexAgentProperties properties) {
        FlexAgentProperties.MemoryProperties memProps = properties.getMemory();
        if ("redis".equalsIgnoreCase(memProps.getType())) {
            FlexAgentProperties.MemoryProperties.RedisProperties redisProps = memProps.getRedis();
            
            redis.clients.jedis.JedisPoolConfig poolConfig = new redis.clients.jedis.JedisPoolConfig();
            poolConfig.setMaxTotal(16);
            poolConfig.setMaxIdle(8);
            
            redis.clients.jedis.JedisPool jedisPool;
            if (redisProps.getPassword() != null && !redisProps.getPassword().isEmpty()) {
                jedisPool = new redis.clients.jedis.JedisPool(
                        poolConfig,
                        redisProps.getHost(),
                        redisProps.getPort(),
                        redisProps.getTimeout(),
                        redisProps.getPassword(),
                        redisProps.getDatabase()
                );
            } else {
                jedisPool = new redis.clients.jedis.JedisPool(
                        poolConfig,
                        redisProps.getHost(),
                        redisProps.getPort(),
                        redisProps.getTimeout(),
                        null,
                        redisProps.getDatabase()
                );
            }
            return new org.flexagent.core.memory.RedisAgentMemory(jedisPool, memProps.getTtl());
        } else {
            return new org.flexagent.core.memory.InMemoryAgentMemory(memProps.getTtl());
        }
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "flexagent.openai", name = "api-key")
    public ChatLanguageModel openAiChatModel(FlexAgentProperties properties) {
        FlexAgentProperties.OpenAiProperties openAiProps = properties.getOpenai();
        dev.langchain4j.model.openai.OpenAiChatModel.OpenAiChatModelBuilder builder = 
                dev.langchain4j.model.openai.OpenAiChatModel.builder()
                        .apiKey(openAiProps.getApiKey())
                        .modelName(properties.getModelName());
                        
        if (StringUtils.hasText(openAiProps.getBaseUrl())) {
            builder.baseUrl(openAiProps.getBaseUrl());
        }
        
        return builder.build();
    }

    @Bean
    @ConditionalOnMissingBean
    public FlexAgentChatModel flexAgentChatModel(
            FlexAgentProperties properties,
            ObjectProvider<ChatLanguageModel> delegateModelProvider,
            ObjectProvider<org.flexagent.core.memory.AgentMemory> agentMemoryProvider,
            ApplicationContext applicationContext
    ) {
        // 1. Scan for tools in Spring Context
        List<Object> toolsList = new ArrayList<>();
        String[] beanNames = applicationContext.getBeanNamesForType(Object.class);
        for (String beanName : beanNames) {
            try {
                // Skip spring-internal beans and properties/configuration beans to avoid recursion
                if (beanName.startsWith("org.springframework") || 
                    beanName.contains("flexAgentProperties") || 
                    beanName.contains("flexAgentChatModel")) {
                    continue;
                }
                
                Object bean = applicationContext.getBean(beanName);
                if (bean != null) {
                    boolean hasTool = false;
                    for (java.lang.reflect.Method method : bean.getClass().getDeclaredMethods()) {
                        if (method.isAnnotationPresent(org.flexagent.core.tool.FlexTool.class) ||
                            method.isAnnotationPresent(dev.langchain4j.agent.tool.Tool.class)) {
                            hasTool = true;
                            break;
                        }
                    }
                    if (hasTool) {
                        toolsList.add(bean);
                    }
                }
            } catch (Exception ignored) {
                // Ignore beans that fail to initialize or resolve
            }
        }

        // 2. Build FlexAgentChatModel
        FlexAgentChatModel.Builder builder = FlexAgentChatModel.builder()
                .runtime(properties.getRuntime())
                .binaryPath(properties.getBinaryPath())
                .storageDirectory(properties.getStorageDirectory())
                .modelName(properties.getModelName())
                .thinkingLevel(properties.getThinkingLevel())
                .systemInstruction(properties.getSystemInstruction())
                .thinkingMode(properties.getThinkingMode())
                .toolCallPolicy(properties.getToolCallPolicy())
                .tools(toolsList.toArray());

        org.flexagent.core.memory.AgentMemory memory = agentMemoryProvider.getIfAvailable();
        if (memory != null) {
            builder.memory(memory);
        }

        // Inject delegate model if present
        ChatLanguageModel delegateModel = delegateModelProvider.getIfAvailable();
        if (delegateModel != null) {
            builder.model(delegateModel);
        }

        return builder.build();
    }

    @Bean
    @ConditionalOnMissingBean
    public org.flexagent.core.FlexAgentClient flexAgentClient(FlexAgentChatModel flexAgentChatModel) {
        return flexAgentChatModel.getCoreClient();
    }

    @Bean
    @ConditionalOnClass(name = "reactor.core.publisher.Flux")
    @ConditionalOnMissingBean
    public org.flexagent.langchain4j.FlexAgentReactiveChatModel flexAgentReactiveChatModel(
            FlexAgentChatModel delegate
    ) {
        return new org.flexagent.langchain4j.FlexAgentReactiveChatModel(delegate);
    }
}
