package org.flexagent.rag.tool;

import org.flexagent.core.tool.FlexParam;
import org.flexagent.core.tool.FlexTool;
import org.flexagent.rag.vectorstore.VectorStore;

import java.util.stream.Collectors;

/**
 * A FlexTool that exposes VectorStore search capabilities to the Agent.
 * This enables "Agentic RAG" where the agent autonomously queries the knowledge base.
 */
public class RagSearchTool {

    private final VectorStore vectorStore;
    private final int maxResults;

    public RagSearchTool(VectorStore vectorStore) {
        this(vectorStore, 3);
    }

    public RagSearchTool(VectorStore vectorStore, int maxResults) {
        this.vectorStore = vectorStore;
        this.maxResults = maxResults;
    }

    @FlexTool(
            name = "search_knowledge_base",
            description = "Search the external knowledge base (RAG) for information relevant to the user's query. " +
                          "Use this tool when you need factual information, document contents, or domain-specific knowledge."
    )
    public String searchKnowledgeBase(
            @FlexParam(name = "query", description = "The search query, should be a detailed sentence or question") String query) {
        
        var results = vectorStore.search(query, maxResults);
        
        if (results == null || results.isEmpty()) {
            return "No relevant information found in the knowledge base for the query: '" + query + "'.";
        }

        return results.stream()
                .map(r -> {
                    String source = r.getDocument().getMetadata().containsKey("filename") 
                            ? r.getDocument().getMetadata().get("filename").toString() 
                            : "Unknown Source";
                    return String.format("[Source: %s, Relevance: %.2f]\n%s", 
                            source, r.getScore(), r.getDocument().getContent());
                })
                .collect(Collectors.joining("\n\n---\n\n"));
    }
}
