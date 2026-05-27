package org.flexagent.core.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class ToolCallPolicyTest {

    @Test
    public void testEnumValues() {
        assertEquals(3, ToolCallPolicy.values().length);
        assertEquals(ToolCallPolicy.STRICT, ToolCallPolicy.valueOf("STRICT"));
        assertEquals(ToolCallPolicy.LENIENT, ToolCallPolicy.valueOf("LENIENT"));
        assertEquals(ToolCallPolicy.TEXT_FALLBACK, ToolCallPolicy.valueOf("TEXT_FALLBACK"));
    }
}
