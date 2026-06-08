package org.flexagent.core.orchestration;

import java.util.function.Consumer;

public interface MessageBus {
    void publish(GroupChatMessage message);
    void subscribe(Consumer<GroupChatMessage> listener);
}
