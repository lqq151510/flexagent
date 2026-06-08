package org.flexagent.examples;

import org.flexagent.core.memory.AgentMessage;
import org.flexagent.core.multiagent.AgentNode;
import org.flexagent.core.orchestration.GroupChat;
import org.flexagent.core.orchestration.InMemoryMessageBus;
import org.flexagent.core.orchestration.MessageBus;
import org.flexagent.core.runtime.RuntimeTypes;
import org.flexagent.langchain4j.FlexAgentChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;

import java.util.Collections;
import java.util.Map;

/**
 * A multi-agent group chat demo where two agents (Philosopher and Scientist) debate about the impacts of technology.
 * This demo features dynamic speaking turns based on Round Robin routing, synchronized through a simplified MessageBus.
 * It supports both offline local simulation (fallback) and real LLM invocation (when DEEPSEEK_API_KEY is configured).
 */
public class DebateGroupChatDemo {

    // 1. A simulated AgentNode for offline usage
    private static class MockDebateAgentNode implements AgentNode {
        private final String name;
        private final String description;
        private final String[] debateLines;
        private int turn = 0;

        public MockDebateAgentNode(String name, String description, String[] debateLines) {
            this.name = name;
            this.description = description;
            this.debateLines = debateLines;
        }

        @Override
        public String getName() { return name; }

        @Override
        public String getDescription() { return description; }

        @Override
        public AgentMessage execute(String task, Map<String, Object> context) {
            String reply = debateLines[turn % debateLines.length];
            turn++;
            return AgentMessage.assistant(reply);
        }
    }

    // 2. An executable AgentNode powered by FlexAgentChatModel for online LLM debate
    private static class FlexLlmAgentNode implements AgentNode, AutoCloseable {
        private final String name;
        private final String description;
        private final FlexAgentChatModel model;

        public FlexLlmAgentNode(String name, String description, FlexAgentChatModel model) {
            this.name = name;
            this.description = description;
            this.model = model;
        }

        @Override
        public String getName() { return name; }

        @Override
        public String getDescription() { return description; }

        @Override
        public AgentMessage execute(String task, Map<String, Object> context) {
            // Send the statement to the model, which appends to its context and responds
            String response = model.generate(task);
            return AgentMessage.assistant(response);
        }

        @Override
        public void close() throws Exception {
            if (model != null) {
                model.close();
            }
        }
    }

    public static void main(String[] args) {
        System.out.println("=== FlexAgent Multi-Agent GroupChat Debate ===");

        MessageBus messageBus = new InMemoryMessageBus();

        // Subscribe to chat events and print them to the console
        messageBus.subscribe(chatMsg -> {
            System.out.println("\n\u001B[32m[" + chatMsg.sender() + "]\u001B[0m: " + chatMsg.text());
        });

        GroupChat groupChat = new GroupChat(messageBus);
        groupChat.setRoutingStrategy(GroupChat.RoutingStrategy.ROUND_ROBIN);

        String apiKey = System.getenv("DEEPSEEK_API_KEY");
        boolean isRealLlm = (apiKey != null && !apiKey.isEmpty() && !apiKey.equals("mock-key"));

        String debateTopic = "辩题：科技发展带给人类的是福祉还是精神虚无？";
        System.out.println("\n" + debateTopic);
        System.out.println("--------------------------------------------------");

        if (isRealLlm) {
            System.out.println("DEEPSEEK_API_KEY is configured. Launching real LLM agents...");

            // Construct ChatLanguageModels for the agents
            OpenAiChatModel modelForPhilosopher = OpenAiChatModel.builder()
                    .baseUrl("https://api.deepseek.com/v1")
                    .apiKey(apiKey)
                    .modelName("deepseek-chat")
                    .build();

            OpenAiChatModel modelForScientist = OpenAiChatModel.builder()
                    .baseUrl("https://api.deepseek.com/v1")
                    .apiKey(apiKey)
                    .modelName("deepseek-chat")
                    .build();

            // Construct FlexAgentChatModels representing individual agent configurations
            try (
                FlexLlmAgentNode philosopher = new FlexLlmAgentNode(
                        "Philosopher (哲学家)", 
                        "辩论正方：强调精神虚无与科技的反噬",
                        FlexAgentChatModel.builder()
                                .runtime(RuntimeTypes.LANGCHAIN4J)
                                .model(modelForPhilosopher)
                                .systemInstruction("你是一名哲学家。你正在参与一场辩论，论点是：'科技的过度发展正使人类精神陷入虚无与疏离，应该反思并重回自然和内省'。请根据对方的论点给出深刻、有哲理的正面辩驳，字数控制在150字以内。")
                                .build()
                );
                FlexLlmAgentNode scientist = new FlexLlmAgentNode(
                        "Scientist (科学家)", 
                        "辩论反方：主张科技是人类理性的火种与文明进化的阶梯",
                        FlexAgentChatModel.builder()
                                .runtime(RuntimeTypes.LANGCHAIN4J)
                                .model(modelForScientist)
                                .systemInstruction("你是一名科学家。你正在参与一场辩论，论点是：'科技进步是人类对抗饥饿、疾病、灾难的最有力武器，它解放了生产力使精神文明更繁荣'。请根据对方的驳斥给出逻辑严密、以事实和科学精神为基础的回应，字数控制在150字以内。")
                                .build()
                )
            ) {
                groupChat.addAgentNode(philosopher);
                groupChat.addAgentNode(scientist);

                String lastTurnOutput = "请开始就以下辩题进行辩论：'" + debateTopic + "'。由哲学家先发言。";

                for (int i = 0; i < 4; i++) {
                    AgentNode activeAgent = groupChat.nextAgentNode();
                    AgentMessage reply = activeAgent.execute(lastTurnOutput, Collections.emptyMap());
                    lastTurnOutput = reply.text();

                    groupChat.broadcast(reply, activeAgent.getName());

                    try {
                        Thread.sleep(1500); // Pause for readability
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            } catch (Exception e) {
                System.err.println("Online debate failed: " + e.getMessage());
                e.printStackTrace();
            }
        } else {
            System.out.println("No DEEPSEEK_API_KEY detected. Entering offline simulation.");

            String[] philosopherLines = {
                "科技的发展虽然带来了物质的富足，但却剥夺了人类精神的安宁。我们在社交网络里看似紧密，心灵深处却沦为孤岛。过度沉溺于算法和效率，使我们忘记了如何回归自然、内省生命本身的意义。",
                "科技并非万灵药。自动化消灭了劳动的诗意，人机交互取代了人与人之间有温度的对视。当我们试图通过技术克服一切肉体局限时，我们其实是在消解人性本身——因为痛苦、等待和未知正是生命的底色。",
            };

            String[] scientistLines = {
                "我尊重哲人对精神的关怀，但必须指出，正是科技的进步，才让我们免于绝大多数由于饥饿、疾病和自然灾害带来的肉体痛苦。没有现代医学和现代农业，大多数人甚至无法活到可以探讨精神虚无的年纪。",
                "技术并不是消解人性，而是解放人性。当我们不再需要为温饱劳碌一生，我们才有更多自由去进行艺术创作与哲学思考。技术的局限性可以通过更好的技术与社会规则去修正，而不是因噎废食地退回石器时代。",
            };

            AgentNode philosopher = new MockDebateAgentNode("Philosopher (哲学家)", "辩论正方：主张精神回归与反思科技弊端", philosopherLines);
            AgentNode scientist = new MockDebateAgentNode("Scientist (科学家)", "辩论反方：主张科技进步是人类前行的根本动力", scientistLines);

            groupChat.addAgentNode(philosopher);
            groupChat.addAgentNode(scientist);

            String lastTurnOutput = debateTopic;
            for (int i = 0; i < 4; i++) {
                AgentNode activeAgent = groupChat.nextAgentNode();
                AgentMessage reply = activeAgent.execute(lastTurnOutput, Collections.emptyMap());
                lastTurnOutput = reply.text();

                groupChat.broadcast(reply, activeAgent.getName());

                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        System.out.println("\n--------------------------------------------------");
        System.out.println("Debate finished successfully.");
    }
}
