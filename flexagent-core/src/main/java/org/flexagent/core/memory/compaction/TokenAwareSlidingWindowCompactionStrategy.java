package org.flexagent.core.memory.compaction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TokenAwareSlidingWindowCompactionStrategy<M> implements CompactionStrategy<M> {

    private final int maxTokens;
    private final MessageInspector<M> inspector;

    public TokenAwareSlidingWindowCompactionStrategy(int maxTokens, MessageInspector<M> inspector) {
        this.maxTokens = maxTokens;
        this.inspector = inspector;
    }

    @Override
    public List<M> compact(List<M> messages) {
        if (messages == null || messages.isEmpty()) {
            return new ArrayList<>();
        }

        List<M> systemMessages = new ArrayList<>();
        int currentTokens = 0;

        // 1. Separate system messages (they are always preserved)
        for (M msg : messages) {
            if (inspector.isSystemMessage(msg)) {
                systemMessages.add(msg);
                currentTokens += inspector.estimateTokenCount(msg);
            }
        }

        // 2. Iterate backwards from the most recent message
        List<M> retainedMessages = new ArrayList<>();
        for (int i = messages.size() - 1; i >= 0; i--) {
            M msg = messages.get(i);

            if (inspector.isSystemMessage(msg)) {
                continue; // Already handled
            }

            // Estimate tokens for the single message or tool turn
            int tokensToAdd = inspector.estimateTokenCount(msg);

            // If it's a tool result, we MUST find its corresponding request to ensure paired integrity
            if (inspector.isToolResultMessage(msg)) {
                // Find matching request by scanning backwards
                int requestIdx = -1;
                for (int j = i - 1; j >= 0; j--) {
                    M previousMsg = messages.get(j);
                    if (inspector.isToolRequestMessage(previousMsg) && inspector.isMatchingToolPair(previousMsg, msg)) {
                        requestIdx = j;
                        break;
                    }
                }

                if (requestIdx != -1) {
                    M requestMsg = messages.get(requestIdx);
                    // Add all results mapped to this single request (there could be multiple tool results per request)
                    // For simplicity, we just keep the whole block [requestIdx, i]
                    int blockTokens = 0;
                    for (int k = requestIdx; k <= i; k++) {
                        M blockMsg = messages.get(k);
                        if (!inspector.isSystemMessage(blockMsg)) {
                             blockTokens += inspector.estimateTokenCount(blockMsg);
                        }
                    }

                    if (currentTokens + blockTokens > maxTokens) {
                        break; // Budget exceeded, drop this entire tool turn and stop
                    }

                    // Add block backwards to retainedMessages
                    for (int k = i; k >= requestIdx; k--) {
                        M blockMsg = messages.get(k);
                        if (!inspector.isSystemMessage(blockMsg)) {
                            retainedMessages.add(blockMsg);
                        }
                    }
                    currentTokens += blockTokens;
                    i = requestIdx; // Skip iterator to requestIdx
                    continue;
                }
            } else if (inspector.isToolRequestMessage(msg)) {
                 // We hit a request without a result (maybe still pending). We keep it if within budget.
                 if (currentTokens + tokensToAdd > maxTokens) {
                     break;
                 }
                 retainedMessages.add(msg);
                 currentTokens += tokensToAdd;
                 continue;
            }

            if (currentTokens + tokensToAdd > maxTokens) {
                break; // Limit reached
            }

            retainedMessages.add(msg);
            currentTokens += tokensToAdd;
        }

        // Reverse to restore original chronological order
        Collections.reverse(retainedMessages);

        List<M> result = new ArrayList<>(systemMessages);
        result.addAll(retainedMessages);
        return result;
    }

    @Override
    public boolean shouldCompact(List<M> messages) {
        return estimateTokenCount(messages) > maxTokens;
    }

    @Override
    public String compactionReason(List<M> messages) {
        return "tokens_exceeded_budget";
    }

    @Override
    public int estimateTokenCount(List<M> messages) {
        int total = 0;
        if (messages != null) {
            for (M msg : messages) {
                total += inspector.estimateTokenCount(msg);
            }
        }
        return total;
    }
}
