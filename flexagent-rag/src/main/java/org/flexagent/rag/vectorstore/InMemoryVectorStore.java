package org.flexagent.rag.vectorstore;

import org.flexagent.rag.document.Document;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * A simple in-memory implementation of VectorStore using cosine similarity.
 * Suitable for testing and lightweight RAG applications.
 */
public class InMemoryVectorStore implements VectorStore {

    private final EmbeddingModel embeddingModel;
    private final List<Entry> entries = new CopyOnWriteArrayList<>();

    public InMemoryVectorStore(EmbeddingModel embeddingModel) {
        this.embeddingModel = Objects.requireNonNull(embeddingModel, "embeddingModel cannot be null");
    }

    @Override
    public void add(List<Document> documents) {
        for (Document doc : documents) {
            Embedding embedding = embeddingModel.embed(doc.getContent());
            entries.add(new Entry(doc, embedding));
        }
    }

    @Override
    public List<SearchResult> search(String query, int maxResults) {
        Embedding queryEmbedding = embeddingModel.embed(query);

        return entries.stream()
                .map(entry -> new SearchResult(entry.document, cosineSimilarity(queryEmbedding, entry.embedding)))
                .sorted(Comparator.comparingDouble(SearchResult::getScore).reversed())
                .limit(maxResults)
                .collect(Collectors.toList());
    }

    private double cosineSimilarity(Embedding v1, Embedding v2) {
        float[] a = v1.getVector();
        float[] b = v2.getVector();
        if (a.length != b.length) {
            throw new IllegalArgumentException("Embeddings must have the same dimensions");
        }

        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < a.length; i++) {
            dotProduct += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }

        if (normA == 0.0 || normB == 0.0) {
            return 0.0;
        }

        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private static class Entry {
        final Document document;
        final Embedding embedding;

        Entry(Document document, Embedding embedding) {
            this.document = document;
            this.embedding = embedding;
        }
    }
}
