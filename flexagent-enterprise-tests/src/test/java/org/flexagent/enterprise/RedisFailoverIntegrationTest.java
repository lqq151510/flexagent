package org.flexagent.enterprise;

import org.flexagent.core.memory.AgentMessage;
import org.flexagent.core.multiagent.AgentNode;
import org.flexagent.core.workflow.AgentTaskNode;
import org.flexagent.core.workflow.CheckpointManager;
import org.flexagent.core.workflow.WorkflowOrchestrator;
import org.flexagent.core.workflow.WorkflowState;

import org.junit.jupiter.api.Test;
import redis.clients.jedis.Jedis;

import java.io.*;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class RedisFailoverIntegrationTest {

    private static class RedisCheckpointManager implements CheckpointManager {
        private final String host;
        private final int port;

        public RedisCheckpointManager(String host, int port) {
            this.host = host;
            this.port = port;
        }

        @Override
        public void save(WorkflowState state) {
            try (Jedis jedis = new Jedis(host, port)) {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                ObjectOutputStream oos = new ObjectOutputStream(bos);
                oos.writeObject(state);
                oos.flush();
                jedis.set(state.getWorkflowId().getBytes(), bos.toByteArray());
            } catch (IOException e) {
                throw new RuntimeException("Failed to serialize workflow state", e);
            }
        }

        @Override
        public WorkflowState load(String workflowId) {
            try (Jedis jedis = new Jedis(host, port)) {
                byte[] data = jedis.get(workflowId.getBytes());
                if (data == null) return null;
                
                ByteArrayInputStream bis = new ByteArrayInputStream(data);
                ObjectInputStream ois = new ObjectInputStream(bis);
                return (WorkflowState) ois.readObject();
            } catch (Exception e) {
                throw new RuntimeException("Failed to deserialize workflow state", e);
            }
        }
        
        public boolean contains(String workflowId) {
            try (Jedis jedis = new Jedis(host, port)) {
                return jedis.exists(workflowId);
            }
        }
    }

    private static class CrashableAgentNode implements AgentNode, Serializable {
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
                throw new RuntimeException("Simulated Node A Crash in Docker Integration!");
            }
            return AgentMessage.assistant("Done by " + name);
        }
        
        public int getRunCount() { return runCount.get(); }
    }

    @Test
    public void testDistributedFailoverWithRealRedis() {
        String host = "127.0.0.1";
        int port = 6379;
        
        RedisCheckpointManager redisManager = new RedisCheckpointManager(host, port);

        // --- Node A Simulation ---
        WorkflowOrchestrator nodeA = new WorkflowOrchestrator(redisManager);
        CrashableAgentNode agent1 = new CrashableAgentNode("A1", true); 
        CrashableAgentNode agent2 = new CrashableAgentNode("A2", false);
        
        nodeA.addNode(new AgentTaskNode("step1", agent1, Collections.singletonList("step2"), "in", "out1"));
        nodeA.addNode(new AgentTaskNode("step2", agent2, null, "in", "out2"));

        try {
            nodeA.run("W-RedisFailover-1", "step1", new HashMap<>());
            fail("Node A should have crashed!");
        } catch (Exception e) {
            assertEquals("Simulated Node A Crash in Docker Integration!", e.getMessage());
        }

        // Verify state is inside the Real Redis Database
        assertTrue(redisManager.contains("W-RedisFailover-1"));
        assertEquals(1, agent1.getRunCount(), "Agent1 should run exactly once before crash");
        
        // --- Node B Takeover Simulation ---
        WorkflowOrchestrator nodeB = new WorkflowOrchestrator(redisManager);
        CrashableAgentNode newAgent1 = new CrashableAgentNode("A1", false); // Fixed
        CrashableAgentNode newAgent2 = new CrashableAgentNode("A2", false);
        
        nodeB.addNode(new AgentTaskNode("step1", newAgent1, Collections.singletonList("step2"), "in", "out1"));
        nodeB.addNode(new AgentTaskNode("step2", newAgent2, null, "in", "out2"));

        System.out.println("Node B connecting to Redis at " + host + ":" + port + " to take over workflow W-RedisFailover-1...");
        
        String finalResult = nodeB.run("W-RedisFailover-1", "step1", new HashMap<>());

        // Node B agent 1 had to re-run step 1, so its count goes to 1
        assertEquals(1, newAgent1.getRunCount());
        assertEquals(1, newAgent2.getRunCount());
    }
}
