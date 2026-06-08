package org.flexagent.benchmark;

import org.flexagent.core.FlexAgentClient;
import org.flexagent.langchain4j.FlexAgentChatModel;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import org.openjdk.jmh.annotations.*;

import java.util.List;
import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 2, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 3, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class FacadeBenchmark {

    private ChatLanguageModel nativeModel;
    private FlexAgentChatModel flexModel;

    @Setup(Level.Trial)
    public void setup() {
        // Mock Model that returns instantly to isolate CPU overhead of the abstraction layer
        nativeModel = new ChatLanguageModel() {
            @Override
            public Response<AiMessage> generate(List<ChatMessage> messages) {
                return Response.from(AiMessage.from("mock response"));
            }
        };

        flexModel = FlexAgentChatModel.builder()
                .delegateModel(nativeModel)
                .build();
    }

    @Benchmark
    public Response<AiMessage> testNativeLangChain4j() {
        return nativeModel.generate(List.of(new dev.langchain4j.data.message.UserMessage("Hello benchmark")));
    }

    @Benchmark
    public Response<AiMessage> testFlexAgentFacade() {
        return flexModel.generate(List.of(new dev.langchain4j.data.message.UserMessage("Hello benchmark")));
    }
}
