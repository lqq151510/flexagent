package org.flexagent.core.exception;

public class ProviderNotFoundException extends FlexAgentException {
    
    public ProviderNotFoundException(String runtimeType) {
        super(String.format("No AgentRuntimeProvider found for type: '%s'. " +
                "Please verify that you have included the correct adapter module dependency (e.g., flexagent-langchain4j or flexagent-localharness) in your classpath.", 
                runtimeType));
    }

    public ProviderNotFoundException(String runtimeType, int matchedCount) {
        super(String.format("Multiple AgentRuntimeProviders (%d matches) found for type: '%s'. " +
                "Conflict detected on classpath. Please ensure only one provider implementation is registered for this type.", 
                matchedCount, runtimeType));
    }
}
