package org.flexagent.benchmark;

import org.flexagent.core.model.Step;
import org.flexagent.core.model.StepStatus;
import org.flexagent.core.model.StepType;
import org.flexagent.core.runtime.AgentRuntime;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

public class AgentEvaluatorTest {

    @Test
    public void testEvaluateSuccess() throws Exception {
        AgentRuntime mockRuntime = Mockito.mock(AgentRuntime.class);

        BenchmarkTask task = new BenchmarkTask("1", "Hello", "world");
        List<BenchmarkTask> tasks = Collections.singletonList(task);

        Step step = new Step("s1", 1, StepType.TEXT_RESPONSE, null, null, StepStatus.DONE, 
                "Hello World", null, null, null, null, null, true, null, null);

        when(mockRuntime.pollStep(anyLong(), eq(TimeUnit.SECONDS))).thenReturn(step);

        AgentEvaluator evaluator = new AgentEvaluator();
        double score = evaluator.evaluate(mockRuntime, tasks);

        assertEquals(1.0, score);
        Mockito.verify(mockRuntime).send("Hello");
        Mockito.verify(mockRuntime).waitForIdle();
    }
    
    @Test
    public void testEvaluateFailure() throws Exception {
        AgentRuntime mockRuntime = Mockito.mock(AgentRuntime.class);

        BenchmarkTask task = new BenchmarkTask("1", "Hello", "world");
        List<BenchmarkTask> tasks = Collections.singletonList(task);

        Step step = new Step("s1", 1, StepType.TEXT_RESPONSE, null, null, StepStatus.DONE, 
                "No match", null, null, null, null, null, true, null, null);

        when(mockRuntime.pollStep(anyLong(), eq(TimeUnit.SECONDS))).thenReturn(step);

        AgentEvaluator evaluator = new AgentEvaluator();
        double score = evaluator.evaluate(mockRuntime, tasks);

        assertEquals(0.0, score);
        Mockito.verify(mockRuntime).send("Hello");
        Mockito.verify(mockRuntime).waitForIdle();
    }
}
