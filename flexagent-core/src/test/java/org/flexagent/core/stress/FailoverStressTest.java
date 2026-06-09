package org.flexagent.core.stress;

import org.flexagent.core.memory.AgentMessage;
import org.flexagent.core.multiagent.AgentNode;
import org.flexagent.core.workflow.AgentTaskNode;
import org.flexagent.core.workflow.CheckpointManager;
import org.flexagent.core.workflow.WorkflowNode;
import org.flexagent.core.workflow.WorkflowOrchestrator;
import org.flexagent.core.workflow.WorkflowState;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class FailoverStressTest {

    // Simulates a distributed Redis/Memcached cache
    private static class SharedCheckpointManager implements CheckpointManager {
        private final Map<String, WorkflowState> clusterStore = new ConcurrentHashMap<>();

        @Override
        public void save(WorkflowState state) {
            clusterStore.put(state.getWorkflowId(), state);
        }

        @Override
        public WorkflowState load(String workflowId) {
            return clusterStore.get(workflowId);
        }
        
        public boolean contains(String workflowId) {
            return clusterStore.containsKey(workflowId);
        }
    }

    private static class CrashableAgentNode implements AgentNode {
        private final String name;
        private final AtomicInteger runCount = new AtomicInteger(0);
        private final boolean crashAfterRun;

        public CrashableAgentNode(String name, boolean crashAfterRun) {
            this.name = name;
            this.crashAfterRun = crashAfterRun;
        }

        @Override
        public String getName() { return name; }

        @Override
        public String getDescription() { return "Node " + name; }

        @Override
        public AgentMessage execute(String task, Map<String, Object> context) {
            runCount.incrementAndGet();
            if (crashAfterRun) {
                // Simulate a severe node failure immediately after saving the state of this execution
                throw new RuntimeException("Simulated Node A Crash!");
            }
            return AgentMessage.assistant("Done by " + name);
        }
        
        public int getRunCount() { return runCount.get(); }
    }

    @Test
    public void testDistributedFailover() {
        SharedCheckpointManager redisMock = new SharedCheckpointManager();

        // Node A Orchestrator
        WorkflowOrchestrator nodeA = new WorkflowOrchestrator(redisMock);
        CrashableAgentNode agent1 = new CrashableAgentNode("A1", true); // Crashes after running
        CrashableAgentNode agent2 = new CrashableAgentNode("A2", false);
        
        nodeA.addNode(new AgentTaskNode("step1", agent1, java.util.Collections.singletonList("step2"), "in", "out1"));
        nodeA.addNode(new AgentTaskNode("step2", agent2, null, "in", "out2"));

        try {
            nodeA.run("W-Failover-1", "step1", new HashMap<>());
            fail("Node A should have crashed!");
        } catch (Exception e) {
            assertEquals("Simulated Node A Crash!", e.getMessage());
        }

        // Verify state is in shared cache and it advanced to step2
        assertTrue(redisMock.contains("W-Failover-1"));
        assertEquals(1, agent1.getRunCount(), "Agent1 should run exactly once before crash");
        assertEquals(0, agent2.getRunCount(), "Agent2 should not have run yet");
        
        // Node B takes over (Complete new process simulation)
        WorkflowOrchestrator nodeB = new WorkflowOrchestrator(redisMock);
        // The node recovered and won't crash this time
        CrashableAgentNode newAgent1 = new CrashableAgentNode("A1", false); 
        CrashableAgentNode newAgent2 = new CrashableAgentNode("A2", false);
        
        nodeB.addNode(new AgentTaskNode("step1", newAgent1, java.util.Collections.singletonList("step2"), "in", "out1"));
        nodeB.addNode(new AgentTaskNode("step2", newAgent2, null, "in", "out2"));

        System.out.println("Node B taking over workflow W-Failover-1...");
        // Node B resumes the workflow. It should load state from redisMock, 
        // see that step1 is done and currentNode is step2.
        String finalResult = nodeB.run("W-Failover-1", "step1", new HashMap<>());

        // Verifications for zero loss
        assertEquals(0, newAgent1.getRunCount(), "Agent1 should NOT run again on Node B");
        assertEquals(1, newAgent2.getRunCount(), "Agent2 should run exactly once on Node B");
    }
}
