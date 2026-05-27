package org.flexagent.core.tool;

import org.junit.jupiter.api.Test;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

public class ToolSchemaGeneratorTest {

    public static class TestTools {
        public void dummyMethod(
                @FlexParam(name = "param1", description = "Test parameter 1", required = true) String p1,
                @FlexParam(name = "param2", description = "Test parameter 2", required = false) Integer p2,
                @FlexParam(name = "param3", description = "Test parameter 3") BigDecimal p3
        ) {}
    }

    @Test
    public void testSchemaGeneration() throws Exception {
        Method method = TestTools.class.getMethod("dummyMethod", String.class, Integer.class, BigDecimal.class);
        String schema = ToolSchemaGenerator.generateSchema(method);
        
        assertNotNull(schema);
        assertTrue(schema.contains("\"type\":\"object\""));
        assertTrue(schema.contains("\"param1\":{\"type\":\"string\",\"description\":\"Test parameter 1\"}"));
        assertTrue(schema.contains("\"param2\":{\"type\":\"integer\",\"description\":\"Test parameter 2\"}"));
        assertTrue(schema.contains("\"param3\":{\"type\":\"number\",\"description\":\"Test parameter 3\"}"));
        assertTrue(schema.contains("\"required\":[\"param1\",\"param3\"]"));
    }
}
