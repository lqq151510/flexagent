package org.flexagent.core.memory.longterm;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryLongTermMemory {
    private final EntityExtractor extractor;
    // Fix applied directly: limiting the size using a bounded approach to prevent OOM
    private final Map<String, String> entities = new ConcurrentHashMap<>();
    private static final int MAX_ENTITIES = 1000;

    public InMemoryLongTermMemory(EntityExtractor extractor) {
        this.extractor = extractor;
    }

    public void saveEntities(String text) {
        Map<String, String> extracted = extractor.extract(text);
        if (extracted != null) {
            for (Map.Entry<String, String> entry : extracted.entrySet()) {
                if (entities.size() >= MAX_ENTITIES) {
                    // Eviction policy: clear the map when full for simplicity
                    entities.clear(); 
                }
                entities.put(entry.getKey(), entry.getValue());
            }
        }
    }

    public Map<String, String> getEntities() {
        return entities;
    }
}
