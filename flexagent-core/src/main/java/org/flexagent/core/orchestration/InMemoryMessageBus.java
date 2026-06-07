package org.flexagent.core.orchestration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class InMemoryMessageBus implements MessageBus {
    
    private final Map<String, List<Consumer<Event>>> listeners = new ConcurrentHashMap<>();

    @Override
    public void publish(Event event) {
        List<Consumer<Event>> topicListeners = listeners.get(event.topic());
        if (topicListeners != null) {
            for (Consumer<Event> listener : topicListeners) {
                listener.accept(event);
            }
        }
    }

    @Override
    public void subscribe(String topic, Consumer<Event> listener) {
        listeners.computeIfAbsent(topic, k -> new CopyOnWriteArrayList<>()).add(listener);
    }
}
