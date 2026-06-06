package org.flexagent.rag.vectorstore;

import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.service.vector.request.InsertReq;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.response.InsertResp;
import io.milvus.v2.service.vector.response.SearchResp;
import org.flexagent.rag.document.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class MilvusVectorStoreTest {

    @Mock
    private MilvusClientV2 client;

    @Mock
    private EmbeddingModel embeddingModel;

    private MilvusVectorStore vectorStore;

    @BeforeEach
    void setUp() {
        vectorStore = new MilvusVectorStore(client, "test_collection", 128, embeddingModel);
    }

    @Test
    void testAddDocuments() {
        Document doc1 = new Document("Test Document 1");
        Document doc2 = new Document("Test Document 2", Collections.singletonMap("source", "web"));

        when(embeddingModel.embed(anyString())).thenReturn(new Embedding(new float[128]));

        InsertResp mockResp = InsertResp.builder().build();
        mockResp.setInsertCnt(2L);
        when(client.insert(any(InsertReq.class))).thenReturn(mockResp);

        vectorStore.add(Arrays.asList(doc1, doc2));

        verify(embeddingModel, times(2)).embed(anyString());
        verify(client, times(1)).insert(any(InsertReq.class));
    }

    @Test
    void testSearchDocuments() {
        when(embeddingModel.embed("query")).thenReturn(new Embedding(new float[128]));

        SearchResp mockResp = SearchResp.builder().build();

        SearchResp.SearchResult resultItem = SearchResp.SearchResult.builder().build();
        resultItem.setScore(0.95f);
        Map<String, Object> entity = new HashMap<>();
        entity.put("text", "Matched Document");
        resultItem.setEntity(entity);
        
        // Milvus v2 search response structure: List of Lists of SearchResult
        mockResp.setSearchResults(Collections.singletonList(Collections.singletonList(resultItem)));

        when(client.search(any(SearchReq.class))).thenReturn(mockResp);

        List<VectorStore.SearchResult> results = vectorStore.search("query", 5);

        assertNotNull(results);
        assertEquals(1, results.size());
        assertEquals("Matched Document", results.get(0).getDocument().getContent());
        assertEquals(0.95f, results.get(0).getScore(), 0.001);

        verify(embeddingModel, times(1)).embed("query");
        verify(client, times(1)).search(any(SearchReq.class));
    }
}
