package org.flexagent.rag.vectorstore;

import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import io.milvus.v2.service.vector.request.InsertReq;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.response.InsertResp;
import io.milvus.v2.service.vector.response.SearchResp;
import com.google.gson.JsonObject;
import org.flexagent.rag.document.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Milvus implementation of VectorStore.
 */
public class MilvusVectorStore implements VectorStore {
    private static final Logger log = LoggerFactory.getLogger(MilvusVectorStore.class);
    
    private final MilvusClientV2 client;
    private final String collectionName;
    private final EmbeddingModel embeddingModel;

    public MilvusVectorStore(String uri, String collectionName, int dimension, EmbeddingModel embeddingModel) {
        this(uri, "", "", collectionName, dimension, embeddingModel);
    }

    public MilvusVectorStore(String uri, String username, String password, String collectionName, int dimension, EmbeddingModel embeddingModel) {
        ConnectConfig connectConfig = ConnectConfig.builder()
                .uri(uri)
                .token(username != null && !username.isEmpty() ? username + ":" + password : null)
                .build();
        this.client = new MilvusClientV2(connectConfig);
        this.collectionName = collectionName;
        this.embeddingModel = embeddingModel;

        initCollection(dimension);
    }

    // Visible for testing
    MilvusVectorStore(MilvusClientV2 client, String collectionName, int dimension, EmbeddingModel embeddingModel) {
        this.client = client;
        this.collectionName = collectionName;
        this.embeddingModel = embeddingModel;
        // Don't call initCollection in test constructor or we can mock it
    }

    private void initCollection(int dimension) {
        boolean exists = client.hasCollection(HasCollectionReq.builder()
                .collectionName(collectionName)
                .build());
        
        if (!exists) {
            log.info("Creating Milvus collection: {}", collectionName);
            CreateCollectionReq createCollectionReq = CreateCollectionReq.builder()
                    .collectionName(collectionName)
                    .dimension(dimension)
                    .build();
            client.createCollection(createCollectionReq);
        }
    }

    @Override
    public void add(List<Document> documents) {
        List<JsonObject> data = new ArrayList<>();
        
        for (Document doc : documents) {
            Embedding embedding = embeddingModel.embed(doc.getContent());
            
            JsonObject jsonObject = new JsonObject();
            // In default QuickSetup of Milvus V2, the vector field is "vector", primary is "id"
            // We use simple schema here for simplicity
            jsonObject.add("vector", createJsonArray(embedding.getVector()));
            jsonObject.addProperty("text", doc.getContent());
            
            // Add metadata if any
            if (doc.getMetadata() != null) {
                for (Map.Entry<String, Object> entry : doc.getMetadata().entrySet()) {
                    jsonObject.addProperty(entry.getKey(), entry.getValue().toString());
                }
            }
            data.add(jsonObject);
        }

        InsertReq insertReq = InsertReq.builder()
                .collectionName(collectionName)
                .data(data)
                .build();
        
        InsertResp resp = client.insert(insertReq);
        log.info("Inserted {} records into Milvus", resp.getInsertCnt());
    }

    @Override
    public List<SearchResult> search(String query, int maxResults) {
        Embedding queryEmbedding = embeddingModel.embed(query);

        SearchReq searchReq = SearchReq.builder()
                .collectionName(collectionName)
                .data(Arrays.asList(new io.milvus.v2.service.vector.request.data.FloatVec(queryEmbedding.getVector())))
                .outputFields(Arrays.asList("text"))
                .topK(maxResults)
                .build();

        SearchResp searchResp = client.search(searchReq);
        
        List<SearchResult> results = new ArrayList<>();
        if (searchResp.getSearchResults() != null && !searchResp.getSearchResults().isEmpty()) {
            List<SearchResp.SearchResult> milvusResults = searchResp.getSearchResults().get(0);
            for (SearchResp.SearchResult r : milvusResults) {
                String text = (String) r.getEntity().get("text");
                Document doc = new Document(text);
                results.add(new SearchResult(doc, r.getScore()));
            }
        }
        return results;
    }
    
    private com.google.gson.JsonArray createJsonArray(float[] vector) {
        com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
        for (float v : vector) {
            arr.add(v);
        }
        return arr;
    }
}
