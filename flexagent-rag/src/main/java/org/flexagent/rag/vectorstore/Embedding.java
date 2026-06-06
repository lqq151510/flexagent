package org.flexagent.rag.vectorstore;

import java.util.Arrays;
import java.util.Objects;

/**
 * Represents a vector embedding.
 */
public class Embedding {
    private final float[] vector;

    public Embedding(float[] vector) {
        this.vector = Objects.requireNonNull(vector, "vector cannot be null");
    }

    public float[] getVector() {
        return vector;
    }

    public int dimension() {
        return vector.length;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Embedding embedding = (Embedding) o;
        return Arrays.equals(vector, embedding.vector);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(vector);
    }
}
