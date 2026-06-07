package org.flexagent.core.orchestration;

import java.util.function.Consumer;

public interface MessageBus {
    void publish(Event event);
    void subscribe(String topic, Consumer<Event> listener);
}
