package org.flexagent.benchmark;

public class BenchmarkTask {
    private String id;
    private String prompt;
    private String expectedOutputPattern;

    public BenchmarkTask() {
    }

    public BenchmarkTask(String id, String prompt, String expectedOutputPattern) {
        this.id = id;
        this.prompt = prompt;
        this.expectedOutputPattern = expectedOutputPattern;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    public String getExpectedOutputPattern() {
        return expectedOutputPattern;
    }

    public void setExpectedOutputPattern(String expectedOutputPattern) {
        this.expectedOutputPattern = expectedOutputPattern;
    }
}
