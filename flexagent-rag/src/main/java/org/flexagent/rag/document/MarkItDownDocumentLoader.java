package org.flexagent.rag.document;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A DocumentLoader that uses Microsoft's 'markitdown' Python CLI tool
 * to convert files (PDF, PPTX, DOCX, XLSX, etc.) into Markdown documents.
 * Note: Requires 'python' and 'markitdown' (pip install markitdown) to be installed locally.
 */
public class MarkItDownDocumentLoader implements DocumentLoader {

    private static final Logger log = LoggerFactory.getLogger(MarkItDownDocumentLoader.class);
    
    private final String pythonExecutable;

    public MarkItDownDocumentLoader() {
        this("python"); // Default to 'python'
    }

    public MarkItDownDocumentLoader(String pythonExecutable) {
        this.pythonExecutable = pythonExecutable;
    }

    @Override
    public List<Document> load(File file) {
        if (file == null || !file.exists()) {
            throw new IllegalArgumentException("File does not exist: " + file);
        }

        try {
            ProcessBuilder pb = new ProcessBuilder(pythonExecutable, "-m", "markitdown", file.getAbsolutePath());
            pb.redirectErrorStream(true);
            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                log.error("markitdown CLI failed with exit code {}. Output: {}", exitCode, output);
                throw new RuntimeException("Failed to convert file using markitdown, exit code: " + exitCode);
            }

            String markdown = output.toString();
            
            Document document = new Document(markdown);
            document.addMetadata("source", file.getAbsolutePath());
            document.addMetadata("filename", file.getName());
            
            return splitByHeaders(document);

        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Error executing markitdown CLI", e);
        }
    }

    @Override
    public List<Document> load(InputStream inputStream) {
        // MarkItDown CLI requires a physical file path.
        // We write the input stream to a temporary file first.
        try {
            File tempFile = File.createTempFile("markitdown-", ".tmp");
            tempFile.deleteOnExit();
            
            try (FileOutputStream out = new FileOutputStream(tempFile)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
            }
            
            return load(tempFile);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write input stream to temp file", e);
        }
    }

    /**
     * A very naive chunking strategy that splits markdown by top-level headers.
     * In a real implementation, a more robust Markdown Splitter should be used.
     */
    private List<Document> splitByHeaders(Document original) {
        String content = original.getContent();
        if (content == null || content.isEmpty()) {
            return Collections.emptyList();
        }

        String[] chunks = content.split("(?=\\n# )");
        List<Document> documents = new ArrayList<>();
        
        for (int i = 0; i < chunks.length; i++) {
            String chunk = chunks[i].trim();
            if (!chunk.isEmpty()) {
                Document doc = new Document(chunk);
                // Copy original metadata
                original.getMetadata().forEach(doc::addMetadata);
                doc.addMetadata("chunk_index", i);
                documents.add(doc);
            }
        }

        if (documents.isEmpty()) {
            documents.add(original);
        }
        
        return documents;
    }
}
