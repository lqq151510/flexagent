package org.flexagent.core.orchestration;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class InMemoryMessageBus implements MessageBus {
    
    private final List<Consumer<GroupChatMessage>> listeners = new CopyOnWriteArrayList<>();

    @Override
    public void publish(GroupChatMessage message) {
        for (Consumer<GroupChatMessage> listener : listeners) {
            listener.accept(message);
        }
    }

    @Override
    public void subscribe(Consumer<GroupChatMessage> listener) {
        listeners.add(listener);
    }
}
