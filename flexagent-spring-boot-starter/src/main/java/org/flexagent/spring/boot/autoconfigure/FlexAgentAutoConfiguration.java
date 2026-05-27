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

import java.util.ArrayList;
import java.util.List;

@AutoConfiguration
@ConditionalOnClass(FlexAgentChatModel.class)
@EnableConfigurationProperties(FlexAgentProperties.class)
public class FlexAgentAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public FlexAgentChatModel flexAgentChatModel(
            FlexAgentProperties properties,
            ObjectProvider<ChatLanguageModel> delegateModelProvider,
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

        // Inject delegate model if present
        ChatLanguageModel delegateModel = delegateModelProvider.getIfAvailable();
        if (delegateModel != null) {
            builder.model(delegateModel);
        }

        return builder.build();
    }
}
