package org.flexagent.core.stress;

import org.flexagent.core.memory.AgentMessage;
import org.flexagent.core.multiagent.AgentNode;
import org.flexagent.core.workflow.AgentTaskNode;
import org.flexagent.core.workflow.ParallelNode;
import org.flexagent.core.workflow.WorkflowOrchestrator;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WorkflowStarvationStressTest {

    private static class SlowAgentNode implements AgentNode {
        private final String name;
        public SlowAgentNode(String name) { this.name = name; }
        @Override public String getName() { return name; }
        @Override public String getDescription() { return "Slow"; }
        @Override
        public AgentMessage execute(String task, Map<String, Object> context) {
            try { Thread.sleep(500); } catch (InterruptedException e) {}
            return AgentMessage.assistant("Done");
        }
    }

    @Test
    public void testParallelNodeStarvation() throws InterruptedException {
        // This test simulates 200 concurrent workflows hitting a ParallelNode.
        // Without an isolated thread pool, this severely clogs the ForkJoinPool.commonPool.
        int numWorkflows = 200;
        ExecutorService starterPool = Executors.newFixedThreadPool(50);
        CountDownLatch latch = new CountDownLatch(numWorkflows);

        WorkflowOrchestrator orchestrator = new WorkflowOrchestrator(null);
        
        AgentTaskNode t1 = new AgentTaskNode("t1", new SlowAgentNode("A"), null, "in", "out1");
        AgentTaskNode t2 = new AgentTaskNode("t2", new SlowAgentNode("B"), null, "in", "out2");
        ParallelNode parallelNode = new ParallelNode("p1", Arrays.asList(t1, t2), null, "comb");
        orchestrator.addNode(parallelNode);

        long start = System.currentTimeMillis();

        for (int i = 0; i < numWorkflows; i++) {
            final String wId = "W-" + i;
            starterPool.submit(() -> {
                try {
                    orchestrator.run(wId, "p1", new HashMap<>());
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        starterPool.shutdown();
        long duration = System.currentTimeMillis() - start;
        System.out.println("Time taken for 200 workflows (each with 2 parallel 500ms tasks): " + duration + "ms");
        
        // In common pool (usually limited to CPU cores), 400 parallel tasks taking 500ms will take a long time.
    }
}
