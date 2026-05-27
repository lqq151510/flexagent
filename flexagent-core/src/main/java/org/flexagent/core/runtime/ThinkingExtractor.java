package org.flexagent.core.runtime;

import org.flexagent.core.model.AgentEvent;
import java.util.List;

public interface ThinkingExtractor {
    List<AgentEvent> extract(String chunk);
}
