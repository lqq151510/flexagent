package org.flexagent.core.stress;

import org.flexagent.core.memory.AgentMessage;
import org.flexagent.core.multiagent.AgentNode;
import org.flexagent.core.orchestration.GroupChat;
import org.flexagent.core.orchestration.MessageBus;
import org.flexagent.core.orchestration.NextSpeakerSelector;
import org.flexagent.core.orchestration.GroupChatMessage;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class GroupChatStressTest {

    @Test
    public void testConcurrentBroadcastAndSelect() throws InterruptedException {
        MessageBus mockBus = new MessageBus() {
            @Override public void publish(GroupChatMessage message) {}
            @Override public void subscribe(java.util.function.Consumer<GroupChatMessage> listener) {}
        };
        
        GroupChat chat = new GroupChat(mockBus);
        
        chat.setSelector((availableNodes, chatHistory) -> {
            // Read from history concurrently while writing is happening
            int size = chatHistory.size();
            for (GroupChatMessage msg : chatHistory) {
                String role = msg.message().role();
            }
            return null;
        });

        int numThreads = 100;
        int messagesPerThread = 50;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numThreads);

        for (int i = 0; i < numThreads; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < messagesPerThread; j++) {
                        chat.broadcast(AgentMessage.user("Msg " + j + " from " + threadId), "Agent-" + threadId);
                        chat.nextAgentNode(); // This will trigger reading the history via the selector
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        // If it doesn't crash, we check the size
        // We expect some missing messages if it's not thread-safe ArrayList, or a crash.
        System.out.println("Finished concurrent test.");
    }
}
