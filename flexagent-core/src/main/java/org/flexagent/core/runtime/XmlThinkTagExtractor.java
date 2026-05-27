package org.flexagent.core.runtime;

import org.flexagent.core.model.AgentEvent;
import org.flexagent.core.model.TextDelta;
import org.flexagent.core.model.ThinkingDelta;

import java.util.ArrayList;
import java.util.List;

public class XmlThinkTagExtractor implements ThinkingExtractor {
    private final StringBuilder buffer = new StringBuilder();
    private boolean inThinkingMode = false;

    private static final String START_TAG = "<think>";
    private static final String END_TAG = "</think>";

    @Override
    public synchronized List<AgentEvent> extract(String chunk) {
        List<AgentEvent> events = new ArrayList<>();
        if (chunk == null || chunk.isEmpty()) {
            return events;
        }

        buffer.append(chunk);

        while (true) {
            String text = buffer.toString();
            if (!inThinkingMode) {
                // Look for "<think>"
                int startIdx = text.indexOf(START_TAG);
                if (startIdx >= 0) {
                    // Emit everything before <think> as TextDelta
                    if (startIdx > 0) {
                        events.add(new TextDelta(text.substring(0, startIdx)));
                    }
                    inThinkingMode = true;
                    // Remove processed text including the tag itself
                    buffer.delete(0, startIdx + START_TAG.length());
                } else {
                    // Check if there is a partial start tag prefix at the end (e.g. "<thi")
                    int possibleStart = findPartialTagStart(text, START_TAG);
                    if (possibleStart >= 0) {
                        // Emit everything before the partial tag and retain the prefix
                        if (possibleStart > 0) {
                            events.add(new TextDelta(text.substring(0, possibleStart)));
                            buffer.delete(0, possibleStart);
                        }
                        break; // Wait for the remaining characters
                    } else {
                        // No full or partial tag, emit all as TextDelta
                        if (!text.isEmpty()) {
                            events.add(new TextDelta(text));
                        }
                        buffer.setLength(0);
                        break;
                    }
                }
            } else {
                // We are in thinking mode, look for "</think>"
                int endIdx = text.indexOf(END_TAG);
                if (endIdx >= 0) {
                    // Emit everything before </think> as ThinkingDelta
                    if (endIdx > 0) {
                        events.add(new ThinkingDelta(text.substring(0, endIdx)));
                    }
                    inThinkingMode = false;
                    buffer.delete(0, endIdx + END_TAG.length());
                } else {
                    // Check if there is a partial end tag prefix at the end (e.g. "</thi")
                    int possibleEnd = findPartialTagStart(text, END_TAG);
                    if (possibleEnd >= 0) {
                        // Emit everything before the partial tag and retain the prefix
                        if (possibleEnd > 0) {
                            events.add(new ThinkingDelta(text.substring(0, possibleEnd)));
                            buffer.delete(0, possibleEnd);
                        }
                        break; // Wait for remaining characters
                    } else {
                        // No full or partial end tag, emit all as ThinkingDelta
                        if (!text.isEmpty()) {
                            events.add(new ThinkingDelta(text));
                        }
                        buffer.setLength(0);
                        break;
                    }
                }
            }
        }

        return events;
    }

    private int findPartialTagStart(String text, String targetTag) {
        for (int len = targetTag.length() - 1; len > 0; len--) {
            String prefix = targetTag.substring(0, len);
            if (text.endsWith(prefix)) {
                return text.length() - len;
            }
        }
        return -1;
    }
}
