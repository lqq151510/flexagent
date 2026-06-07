package org.flexagent.console.config;

import org.flexagent.core.model.ToolDefinition;
import org.flexagent.core.runtime.RuntimeTypes;
import org.flexagent.langchain4j.FlexAgentChatModel;
import org.flexagent.mcp.McpClient;
import org.flexagent.mcp.McpToolExecutor;
import org.flexagent.mcp.McpToolScanner;
import org.flexagent.rag.tool.RagSearchTool;
import org.flexagent.rag.vectorstore.Embedding;
import org.flexagent.rag.vectorstore.EmbeddingModel;
import org.flexagent.rag.vectorstore.InMemoryVectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.ai.openai.OpenAiChatModel;

import java.util.List;
import java.util.ArrayList;

@Configuration
public class AgentConfiguration {

    @Bean(destroyMethod = "close")
    public McpClient mcpClient() throws Exception {
        McpClient client = new McpClient("npx", "-y", "@modelcontextprotocol/server-everything");
        client.start();
        return client;
    }

    @Bean
    public McpToolExecutor mcpToolExecutor(McpClient mcpClient) throws Exception {
        McpToolScanner scanner = new McpToolScanner(mcpClient);
        List<ToolDefinition> mcpTools = scanner.fetchTools();
        List<String> toolNames = mcpTools.stream().map(ToolDefinition::name).toList();
        return new McpToolExecutor(mcpClient, toolNames);
    }

    @Bean
    public EmbeddingModel dummyEmbeddingModel() {
        return new EmbeddingModel() {
            @Override
            public Embedding embed(String text) {
                // Dummy embedding for tests/dev
                return new Embedding(new float[]{0.1f, 0.2f, 0.3f});
            }
        };
    }

    @Bean
    public RagSearchTool ragSearchTool(EmbeddingModel embeddingModel) {
        return new RagSearchTool(new InMemoryVectorStore(embeddingModel));
    }

    @Bean
    public FlexAgentChatModel flexAgentChatModel(OpenAiChatModel springAiModel, McpClient mcpClient, McpToolExecutor mcpToolExecutor, RagSearchTool ragSearchTool) throws Exception {
        McpToolScanner scanner = new McpToolScanner(mcpClient);
        List<ToolDefinition> mcpTools = scanner.fetchTools();
        
        List<Object> tools = new ArrayList<>();
        tools.addAll(mcpTools);
        tools.add(ragSearchTool);

        return FlexAgentChatModel.builder()
                .runtime(RuntimeTypes.SPRING_AI)
                .model(springAiModel)
                .tools(tools.toArray())
                .customToolExecutor(mcpToolExecutor)
                .build();
    }
}
