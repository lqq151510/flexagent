package org.flexagent.spring.boot.observation;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.flexagent.core.observation.AgentObservationRegistry;
import org.flexagent.core.observation.AgentSpan;

/**
 * Micrometer implementation of AgentObservationRegistry.
 */
public class MicrometerAgentObservationRegistry implements AgentObservationRegistry {
    private final ObservationRegistry registry;

    public MicrometerAgentObservationRegistry(ObservationRegistry registry) {
        this.registry = registry;
    }

    @Override
    public AgentSpan startSpan(String name) {
        Observation observation = Observation.createNotStarted(name, registry).start();
        return new AgentSpan() {
            @Override
            public void tag(String key, String value) {
                observation.lowCardinalityKeyValue(key, value);
            }

            @Override
            public void error(Throwable throwable) {
                observation.error(throwable);
            }

            @Override
            public void end() {
                observation.stop();
            }
        };
    }
}
