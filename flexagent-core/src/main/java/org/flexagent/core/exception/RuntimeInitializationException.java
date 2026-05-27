package org.flexagent.core.exception;

public class RuntimeInitializationException extends FlexAgentException {

    public RuntimeInitializationException(String runtimeType, String message) {
        super(String.format("Failed to initialize AgentRuntime for type '%s': %s", runtimeType, message));
    }

    public RuntimeInitializationException(String runtimeType, String message, Throwable cause) {
        super(String.format("Failed to initialize AgentRuntime for type '%s': %s", runtimeType, message), cause);
    }
}
