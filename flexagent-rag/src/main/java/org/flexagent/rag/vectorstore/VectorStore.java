package org.flexagent.rag.vectorstore;

import org.flexagent.rag.document.Document;

import java.util.List;

/**
 * Interface for vector stores capable of storing and searching embedded documents.
 */
public interface VectorStore {

    /**
     * Adds a list of documents to the vector store.
     * The vector store will typically embed the documents if they aren't embedded yet,
     * or rely on an external EmbeddingModel.
     *
     * @param documents the documents to add
     */
    void add(List<Document> documents);

    /**
     * Searches for documents similar to the given query text.
     *
     * @param query the search query
     * @param maxResults the maximum number of results to return
     * @return a list of search results containing the matching documents and their similarity scores
     */
    List<SearchResult> search(String query, int maxResults);

    /**
     * Represents a search result containing the matched document and its score.
     */
    class SearchResult {
        private final Document document;
        private final double score;

        public SearchResult(Document document, double score) {
            this.document = document;
            this.score = score;
        }

        public Document getDocument() {
            return document;
        }

        public double getScore() {
            return score;
        }
    }
}
