package org.flexagent.localharness;

import org.flexagent.core.runtime.AgentRuntime;
import org.flexagent.core.runtime.AgentRuntimeConfig;
import org.flexagent.core.runtime.AgentRuntimeProvider;
import org.flexagent.core.runtime.RuntimeTypes;

public class LocalHarnessRuntimeProvider implements AgentRuntimeProvider {
    @Override
    public boolean supports(String type) {
        return RuntimeTypes.LOCAL_HARNESS.equalsIgnoreCase(type);
    }

    @Override
    public AgentRuntime create(AgentRuntimeConfig config) {
        return new LocalHarnessRuntime();
    }
}
