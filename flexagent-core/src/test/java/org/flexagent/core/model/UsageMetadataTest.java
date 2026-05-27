package org.flexagent.core.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class UsageMetadataTest {

    @Test
    public void testRecordFields() {
        UsageMetadata metadata = new UsageMetadata(100, 20, 50, 30, 200);

        assertEquals(100, metadata.promptTokenCount());
        assertEquals(20, metadata.cachedContentTokenCount());
        assertEquals(50, metadata.candidatesTokenCount());
        assertEquals(30, metadata.thoughtsTokenCount());
        assertEquals(200, metadata.totalTokenCount());
    }

    @Test
    public void testRecordEqualityAndHashCode() {
        UsageMetadata m1 = new UsageMetadata(10, 20, 30, 40, 100);
        UsageMetadata m2 = new UsageMetadata(10, 20, 30, 40, 100);
        UsageMetadata m3 = new UsageMetadata(11, 20, 30, 40, 101);

        assertEquals(m1, m2);
        assertNotEquals(m1, m3);
        assertEquals(m1.hashCode(), m2.hashCode());
    }
}
