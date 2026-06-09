package org.flexagent.core.workflow;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * A WorkflowNode that executes multiple sub-nodes in parallel.
 */
public class ParallelNode implements WorkflowNode {
    private final String id;
    private final List<WorkflowNode> parallelTasks;
    private final List<String> nextNodeIds;
    private final String outputKey;

    public ParallelNode(String id, List<WorkflowNode> parallelTasks, List<String> nextNodeIds, String outputKey) {
        this.id = id;
        this.parallelTasks = parallelTasks;
        this.nextNodeIds = nextNodeIds != null ? nextNodeIds : Collections.emptyList();
        this.outputKey = outputKey;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String execute(WorkflowState state) {
        List<CompletableFuture<String>> futures = new ArrayList<>();
        java.util.concurrent.ExecutorService executor = state.getExecutorService();
        
        for (WorkflowNode task : parallelTasks) {
            if (executor != null) {
                futures.add(CompletableFuture.supplyAsync(() -> task.execute(state), executor));
            } else {
                futures.add(CompletableFuture.supplyAsync(() -> task.execute(state)));
            }
        }
        
        CompletableFuture<Void> allDone = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
        allDone.join(); // Wait for all to complete
        
        List<String> results = futures.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList());
                
        String combinedResult = String.join("\n---\n", results);
        state.setContextVariable(outputKey, combinedResult);
        return combinedResult;
    }

    @Override
    public List<String> getNextNodeIds() {
        return nextNodeIds;
    }
}
