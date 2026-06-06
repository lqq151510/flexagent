package org.flexagent.core.exception;

/**
 * Exception thrown when a model API rate limit or quota is exceeded.
 */
public class RateLimitException extends FlexAgentException {
    public RateLimitException(String message) {
        super(message);
    }

    public RateLimitException(String message, Throwable cause) {
        super(message, cause);
    }
}
