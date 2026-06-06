package org.flexagent.rag.document;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Represents a document or a chunk of a document with its text content and metadata.
 */
public class Document {
    private final String content;
    private final Map<String, Object> metadata;

    public Document(String content) {
        this(content, new HashMap<>());
    }

    public Document(String content, Map<String, Object> metadata) {
        this.content = Objects.requireNonNull(content, "content cannot be null");
        this.metadata = metadata != null ? new HashMap<>(metadata) : new HashMap<>();
    }

    public String getContent() {
        return content;
    }

    public Map<String, Object> getMetadata() {
        return Collections.unmodifiableMap(metadata);
    }

    public void addMetadata(String key, Object value) {
        this.metadata.put(key, value);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Document document = (Document) o;
        return Objects.equals(content, document.content) && Objects.equals(metadata, document.metadata);
    }

    @Override
    public int hashCode() {
        return Objects.hash(content, metadata);
    }

    @Override
    public String toString() {
        return "Document{" +
                "content='" + (content.length() > 50 ? content.substring(0, 50) + "..." : content) + '\'' +
                ", metadata=" + metadata +
                '}';
    }
}
