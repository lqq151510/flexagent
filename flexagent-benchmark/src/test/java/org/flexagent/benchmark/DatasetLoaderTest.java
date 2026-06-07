package org.flexagent.benchmark;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class DatasetLoaderTest {

    @Test
    public void testLoadJson() throws Exception {
        String json = "[{\"id\":\"1\", \"prompt\":\"Test\", \"expectedOutputPattern\":\"Test\"}]";
        InputStream is = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
        
        DatasetLoader loader = new DatasetLoader();
        List<BenchmarkTask> tasks = loader.loadJson(is);
        
        assertNotNull(tasks);
        assertEquals(1, tasks.size());
        assertEquals("1", tasks.get(0).getId());
        assertEquals("Test", tasks.get(0).getPrompt());
        assertEquals("Test", tasks.get(0).getExpectedOutputPattern());
    }
}
