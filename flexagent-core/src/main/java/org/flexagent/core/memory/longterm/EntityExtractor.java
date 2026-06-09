package org.flexagent.core.memory.longterm;

import java.util.Map;

public interface EntityExtractor {
    Map<String, String> extract(String text);
}
