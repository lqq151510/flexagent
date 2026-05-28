package org.flexagent.core.memory;

public class AgentSessionContext {
    private static final ThreadLocal<String> CURRENT_SESSION_ID = new ThreadLocal<>();

    public static String get() {
        return CURRENT_SESSION_ID.get();
    }

    public static void set(String sessionId) {
        CURRENT_SESSION_ID.set(sessionId);
    }

    public static void clear() {
        CURRENT_SESSION_ID.remove();
    }
}
