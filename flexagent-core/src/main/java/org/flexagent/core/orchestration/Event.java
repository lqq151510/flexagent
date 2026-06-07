package org.flexagent.core.orchestration;

public record Event(String topic, String source, Object payload) {}
