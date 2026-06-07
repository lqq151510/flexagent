package org.flexagent.benchmark;

import org.flexagent.core.model.Step;
import org.flexagent.core.model.StepStatus;
import org.flexagent.core.runtime.AgentRuntime;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public class AgentEvaluator {

    public double evaluate(AgentRuntime runtime, List<BenchmarkTask> tasks) {
        if (tasks == null || tasks.isEmpty()) return 0.0;
        int successCount = 0;

        for (BenchmarkTask task : tasks) {
            try {
                runtime.send(task.getPrompt());
                
                StringBuilder output = new StringBuilder();
                boolean isDone = false;
                
                while (!isDone) {
                    Step step = runtime.pollStep(10, TimeUnit.SECONDS);
                    if (step == null) {
                        break;
                    }
                    
                    if (step.contentDelta() != null) {
                        output.append(step.contentDelta());
                    } else if (step.content() != null && step.status() == StepStatus.DONE) {
                        output.append(step.content());
                    }

                    if (step.status() == StepStatus.DONE || step.status() == StepStatus.ERROR) {
                        isDone = true;
                    }
                }
                
                String finalOutput = output.toString();
                if (task.getExpectedOutputPattern() != null && !task.getExpectedOutputPattern().isEmpty()) {
                    Pattern pattern = Pattern.compile(task.getExpectedOutputPattern(), Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
                    if (pattern.matcher(finalOutput).find()) {
                        successCount++;
                    }
                } else {
                    if (!finalOutput.trim().isEmpty()) {
                        successCount++;
                    }
                }
                
                runtime.waitForIdle();
            } catch (Exception e) {
                // Ignore error, consider as failure
            }
        }

        return (double) successCount / tasks.size();
    }
}
