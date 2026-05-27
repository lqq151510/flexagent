package org.flexagent.core.exception;

public class FlexAgentException extends RuntimeException {
    
    public FlexAgentException(String message) {
        super(message);
    }

    public FlexAgentException(String message, Throwable cause) {
        super(message, cause);
    }
}
