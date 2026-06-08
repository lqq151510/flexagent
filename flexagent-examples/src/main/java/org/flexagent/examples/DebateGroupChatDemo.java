package org.flexagent.examples;

import org.flexagent.core.memory.AgentMessage;
import org.flexagent.core.multiagent.AgentNode;
import org.flexagent.core.multiagent.JudgeAgentNode;
import org.flexagent.core.orchestration.GroupChat;
import org.flexagent.core.orchestration.InMemoryMessageBus;
import org.flexagent.core.orchestration.MessageBus;
import org.flexagent.core.orchestration.RoundRobinSpeakerSelector;
import org.flexagent.core.orchestration.LlmSupervisorSelector;
import org.flexagent.core.runtime.RuntimeTypes;
import org.flexagent.langchain4j.FlexAgentChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;

import java.util.Collections;
import java.util.Map;

/**
 * A multi-agent group chat demo where agents debate about the impacts of technology.
 * This demo features dynamic LLM-based Supervisor routing and a Judge agent to conclude the debate.
 */
public class DebateGroupChatDemo {

    // 1. Simulated AgentNode for offline usage
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

    // 2. Executable AgentNode powered by FlexAgentChatModel for online LLM debate
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
        System.out.println("=== FlexAgent Multi-Agent GroupChat Debate (v1.3.0 LLM Supervisor) ===");

        MessageBus messageBus = new InMemoryMessageBus();
        messageBus.subscribe(chatMsg -> {
            System.out.println("\n\u001B[32m[" + chatMsg.sender() + "]\u001B[0m: " + chatMsg.text());
        });

        GroupChat groupChat = new GroupChat(messageBus);

        String apiKey = System.getenv("DEEPSEEK_API_KEY");
        boolean isRealLlm = (apiKey != null && !apiKey.isEmpty() && !apiKey.equals("mock-key"));

        String debateTopic = "辩题：科技发展带给人类的是福祉还是精神虚无？";
        System.out.println("\n" + debateTopic);
        System.out.println("--------------------------------------------------");

        if (isRealLlm) {
            System.out.println("DEEPSEEK_API_KEY is configured. Launching real LLM agents with Supervisor Router...");

            OpenAiChatModel baseModel = OpenAiChatModel.builder()
                    .baseUrl("https://api.deepseek.com/v1")
                    .apiKey(apiKey)
                    .modelName("deepseek-chat")
                    .build();

            // Set up LlmSupervisorSelector to route dynamically
            groupChat.setSelector(new LlmSupervisorSelector(prompt -> baseModel.generate(prompt)));

            try (
                FlexLlmAgentNode philosopher = new FlexLlmAgentNode(
                        "Philosopher", 
                        "正方哲学家：强调精神虚无与科技反噬，反对技术绝对主义",
                        FlexAgentChatModel.builder()
                                .runtime(RuntimeTypes.LANGCHAIN4J)
                                .model(baseModel)
                                .systemInstruction("你是一名哲学家。你正在参与辩论：'科技过度发展正使人类精神陷入虚无'。请给出深刻、有哲理的辩驳，字数控制在100字以内。")
                                .build()
                );
                FlexLlmAgentNode scientist = new FlexLlmAgentNode(
                        "Scientist", 
                        "反方科学家：主张科技进步是人类前行的根本动力",
                        FlexAgentChatModel.builder()
                                .runtime(RuntimeTypes.LANGCHAIN4J)
                                .model(baseModel)
                                .systemInstruction("你是一名科学家。你正在参与辩论：'科技进步是人类对抗灾难的最有力武器'。请给出逻辑严密、以事实为基础的回应，字数控制在100字以内。")
                                .build()
                );
                FlexLlmAgentNode judgeModel = new FlexLlmAgentNode(
                        "Judge", 
                        "裁判：当双方已经充分表达意见后（辩论超过3轮），应当被选中进行总结、宣告获胜方并终止辩论。",
                        FlexAgentChatModel.builder()
                                .runtime(RuntimeTypes.LANGCHAIN4J)
                                .model(baseModel)
                                .systemInstruction("你是辩论的裁判。阅读场上内容，给出一个100字以内的公允总结，并判定哪方更有说服力。")
                                .build()
                )
            ) {
                // Wrap judge in JudgeAgentNode to signal termination to GroupChat
                AgentNode judge = new JudgeAgentNode("Judge", judgeModel.getDescription(), groupChat, judgeModel);

                groupChat.addAgentNode(philosopher);
                groupChat.addAgentNode(scientist);
                groupChat.addAgentNode(judge);

                String lastTurnOutput = "请开始就以下辩题进行辩论：'" + debateTopic + "'。";

                while (!groupChat.isFinished()) {
                    AgentNode activeAgent = groupChat.nextAgentNode();
                    if (activeAgent == null) break;
                    
                    AgentMessage reply = activeAgent.execute(lastTurnOutput, Collections.emptyMap());
                    lastTurnOutput = reply.text();
                    groupChat.broadcast(reply, activeAgent.getName());
                    
                    Thread.sleep(2000);
                }
            } catch (Exception e) {
                System.err.println("Online debate failed: " + e.getMessage());
                e.printStackTrace();
            }
        } else {
            System.out.println("No DEEPSEEK_API_KEY detected. Entering offline simulation.");
            groupChat.setSelector(new RoundRobinSpeakerSelector());

            String[] philosopherLines = {
                "科技的发展虽然带来了物质的富足，但却剥夺了人类精神的安宁。",
                "当我们试图通过技术克服一切肉体局限时，我们其实是在消解人性本身。"
            };
            String[] scientistLines = {
                "正是科技的进步，才让我们免于绝大多数由于饥饿、疾病和自然灾害带来的肉体痛苦。",
                "技术并不是消解人性，而是解放人性。"
            };
            String[] judgeLines = {
                "总结陈词：哲学家与科学家都有其合理的关切，辩论结束。"
            };

            AgentNode philosopher = new MockDebateAgentNode("Philosopher", "辩论正方", philosopherLines);
            AgentNode scientist = new MockDebateAgentNode("Scientist", "辩论反方", scientistLines);
            AgentNode judgeInner = new MockDebateAgentNode("Judge", "裁判", judgeLines);
            AgentNode judge = new JudgeAgentNode("Judge", "裁判", groupChat, judgeInner);

            groupChat.addAgentNode(philosopher);
            groupChat.addAgentNode(scientist);
            groupChat.addAgentNode(judge);

            String lastTurnOutput = debateTopic;
            while (!groupChat.isFinished()) {
                AgentNode activeAgent = groupChat.nextAgentNode();
                if (activeAgent == null) break;

                AgentMessage reply = activeAgent.execute(lastTurnOutput, Collections.emptyMap());
                lastTurnOutput = reply.text();
                groupChat.broadcast(reply, activeAgent.getName());

                try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
            }
        }

        System.out.println("\n--------------------------------------------------");
        System.out.println("Debate finished successfully.");
    }
}
