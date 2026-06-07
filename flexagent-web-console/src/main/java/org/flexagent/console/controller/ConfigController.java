package org.flexagent.console.controller;

import org.flexagent.mcp.McpToolExecutor;
import org.flexagent.rag.tool.RagSearchTool;
import org.flexagent.rag.vectorstore.EmbeddingModel;
import org.flexagent.rag.vectorstore.InMemoryVectorStore;
import org.flexagent.rag.vectorstore.MilvusVectorStore;
import org.flexagent.rag.vectorstore.VectorStore;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.List;

@RestController
@RequestMapping("/api/config")
public class ConfigController {

    private final RagSearchTool ragSearchTool;
    private final McpToolExecutor mcpToolExecutor;
    private final EmbeddingModel embeddingModel;

    public ConfigController(RagSearchTool ragSearchTool, McpToolExecutor mcpToolExecutor, EmbeddingModel embeddingModel) {
        this.ragSearchTool = ragSearchTool;
        this.mcpToolExecutor = mcpToolExecutor;
        this.embeddingModel = embeddingModel;
    }

    @PostMapping("/rag")
    public Map<String, String> updateRagConfig(@RequestBody Map<String, String> payload) {
        String storeType = payload.getOrDefault("store", "in-memory");
        
        VectorStore newStore;
        if ("milvus".equalsIgnoreCase(storeType)) {
            String uri = payload.getOrDefault("uri", "http://localhost:19530");
            String username = payload.getOrDefault("username", "");
            String password = payload.getOrDefault("password", "");
            String collectionName = payload.getOrDefault("collectionName", "flexagent_docs");
            int dimension = Integer.parseInt(payload.getOrDefault("dimension", "128"));
            
            newStore = new MilvusVectorStore(uri, username, password, collectionName, dimension, embeddingModel);
        } else {
            newStore = new InMemoryVectorStore(embeddingModel);
        }

        ragSearchTool.setVectorStore(newStore);
        return Map.of("status", "success", "message", "RAG configuration updated to " + storeType);
    }

    @PostMapping("/mcp")
    public Map<String, String> updateMcpConfig(@RequestBody Map<String, Object> payload) {
        if (payload.containsKey("tools")) {
            List<String> tools = (List<String>) payload.get("tools");
            mcpToolExecutor.setSupportedTools(tools);
            return Map.of("status", "success", "message", "MCP tools updated");
        }
        return Map.of("status", "error", "message", "Missing 'tools' field");
    }
}
