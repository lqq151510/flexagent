package org.flexagent.rag.document;

import java.io.File;
import java.io.InputStream;
import java.util.List;

/**
 * Interface for loading documents from various sources.
 */
public interface DocumentLoader {

    /**
     * Loads documents from a local file.
     *
     * @param file the file to load
     * @return a list of documents
     */
    List<Document> load(File file);

    /**
     * Loads documents from an input stream.
     *
     * @param inputStream the input stream to load
     * @return a list of documents
     */
    List<Document> load(InputStream inputStream);
}
