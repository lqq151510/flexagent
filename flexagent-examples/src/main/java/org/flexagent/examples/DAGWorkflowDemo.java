package org.flexagent.examples;

import org.flexagent.core.memory.AgentMessage;
import org.flexagent.core.multiagent.AgentNode;
import org.flexagent.core.workflow.AgentTaskNode;
import org.flexagent.core.workflow.CheckpointManager;
import org.flexagent.core.workflow.InMemoryCheckpointManager;
import org.flexagent.core.workflow.ParallelNode;
import org.flexagent.core.workflow.WorkflowOrchestrator;
import org.flexagent.core.workflow.WorkflowState;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Demonstrates a complex DAG workflow utilizing Checkpointing and Parallel execution.
 */
public class DAGWorkflowDemo {

    private static class MockAgentNode implements AgentNode {
        private final String name;
        private final String logicPrefix;

        public MockAgentNode(String name, String logicPrefix) {
            this.name = name;
            this.logicPrefix = logicPrefix;
        }

        @Override
        public String getName() { return name; }

        @Override
        public String getDescription() { return "A mock agent for workflow"; }

        @Override
        public AgentMessage execute(String task, Map<String, Object> context) {
            System.out.println("[" + name + "] Executing task: " + task);
            try { Thread.sleep(500); } catch (InterruptedException ignored) {} // simulate work
            return AgentMessage.assistant(logicPrefix + " processed: " + task);
        }
    }

    public static void main(String[] args) {
        System.out.println("=== FlexAgent Workflow DAG Demo (v1.4.0) ===");
        
        CheckpointManager checkpointManager = new InMemoryCheckpointManager();
        WorkflowOrchestrator orchestrator = new WorkflowOrchestrator(checkpointManager);

        // 1. Define nodes
        AgentNode researchAgent = new MockAgentNode("ResearchAgent", "Research data");
        AgentNode codingAgent = new MockAgentNode("CodingAgent", "Code generated");
        AgentNode reviewAgent = new MockAgentNode("ReviewAgent", "Review completed");

        // The workflow topology:
        // Research (Node A) -> Parallel Split (Node B: Coding) -> Join Review (Node C)
        
        AgentTaskNode nodeA = new AgentTaskNode(
                "nodeA", researchAgent, List.of("nodeB"), "input_topic", "research_result");
                
        // For demonstration, Parallel node wraps multiple Agents. They both get state from state context.
        AgentTaskNode parallelTask1 = new AgentTaskNode("p1", codingAgent, null, "research_result", "code_out_1");
        AgentTaskNode parallelTask2 = new AgentTaskNode("p2", new MockAgentNode("DocAgent", "Doc created"), null, "research_result", "doc_out_1");
        
        ParallelNode nodeB = new ParallelNode("nodeB", Arrays.asList(parallelTask1, parallelTask2), List.of("nodeC"), "combined_artifacts");
        
        AgentTaskNode nodeC = new AgentTaskNode(
                "nodeC", reviewAgent, null, "combined_artifacts", "final_review");

        orchestrator.addNode(nodeA);
        orchestrator.addNode(nodeB);
        orchestrator.addNode(nodeC);

        // 2. Start execution
        String workflowId = "workflow-run-101";
        Map<String, Object> initialCtx = new HashMap<>();
        initialCtx.put("input_topic", "Create a basic HTTP server");

        System.out.println("Starting workflow...");
        orchestrator.run(workflowId, "nodeA", initialCtx);

        // 3. Verify state and checkpoint
        WorkflowState finalState = checkpointManager.load(workflowId);
        System.out.println("\n=== Workflow Completed ===");
        System.out.println("Is Finished: " + finalState.isFinished());
        System.out.println("Final Output:\n" + finalState.getFinalResult());
    }
}
