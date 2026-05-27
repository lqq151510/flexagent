package org.flexagent.core.runtime;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class RuntimeTypesTest {

    @Test
    public void testConstantValues() {
        assertEquals("langchain4j", RuntimeTypes.LANGCHAIN4J);
        assertEquals("localharness", RuntimeTypes.LOCAL_HARNESS);
    }
}
