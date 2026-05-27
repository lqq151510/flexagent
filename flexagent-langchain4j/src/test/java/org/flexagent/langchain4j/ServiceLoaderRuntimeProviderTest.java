package org.flexagent.langchain4j;

import org.flexagent.core.runtime.AgentRuntimeProvider;
import org.flexagent.core.runtime.RuntimeTypes;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.ServiceLoader;

import static org.junit.jupiter.api.Assertions.*;

public class ServiceLoaderRuntimeProviderTest {

    @Test
    public void testSpiLoaderLoadsLangChain4jProvider() {
        ServiceLoader<AgentRuntimeProvider> loader = ServiceLoader.load(AgentRuntimeProvider.class);
        List<AgentRuntimeProvider> providers = loader.stream()
                .map(ServiceLoader.Provider::get)
                .toList();

        assertFalse(providers.isEmpty(), "Providers list should not be empty when langchain4j module is on classpath");

        boolean hasLangChain4j = providers.stream()
                .anyMatch(p -> p.supports(RuntimeTypes.LANGCHAIN4J));
        assertTrue(hasLangChain4j, "Should dynamically discover LangChain4jRuntimeProvider");
    }

    @Test
    public void testSpiLoaderDoesNotSupportUnknownType() {
        ServiceLoader<AgentRuntimeProvider> loader = ServiceLoader.load(AgentRuntimeProvider.class);
        List<AgentRuntimeProvider> providers = loader.stream()
                .map(ServiceLoader.Provider::get)
                .filter(p -> p.supports("unknown-type-xyz"))
                .toList();

        assertTrue(providers.isEmpty(), "Should not find any provider supporting an unknown type");
    }
}
