package org.flexagent.core.stress;

import org.flexagent.core.memory.AgentMessage;
import org.flexagent.core.memory.longterm.InMemoryLongTermMemory;
import org.flexagent.core.memory.longterm.EntityExtractor;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MemoryOOMStressTest {

    @Test
    public void testMemoryUnboundedGrowth() {
        InMemoryLongTermMemory memory = new InMemoryLongTermMemory(new EntityExtractor() {
            @Override
            public Map<String, String> extract(String text) {
                Map<String, String> map = new HashMap<>();
                map.put("Key-" + text.hashCode(), text);
                return map;
            }
        });

        // Add lots of messages to simulate a very long session.
        // In a real system without a window limit, this will eventually OOM.
        try {
            for (int i = 0; i < 50000; i++) {
                // Simulate large context chunks
                StringBuilder sb = new StringBuilder();
                for (int j = 0; j < 100; j++) {
                    sb.append("This is some payload text meant to consume memory. Iteration: ").append(i).append("-").append(j);
                }
                memory.saveEntities(sb.toString());
            }
            
            // If we get here and it hasn't OOMed, the memory might be fine or we didn't add enough.
            // But we should assert that the memory doesn't just grow infinitely.
            int entitiesCount = memory.getEntities().size();
            System.out.println("Final entities count: " + entitiesCount);
            
            // Fix: We need to limit the size of InMemoryLongTermMemory!
            
        } catch (OutOfMemoryError e) {
            System.err.println("OOM Triggered! This indicates a lack of eviction policy.");
            throw e;
        }
    }
}
