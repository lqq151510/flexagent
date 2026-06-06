package org.flexagent.rag.vectorstore;

/**
 * Interface for generating embeddings from text.
 */
public interface EmbeddingModel {

    /**
     * Embeds the given text into a vector space.
     *
     * @param text the text to embed
     * @return the embedding vector
     */
    Embedding embed(String text);
}
