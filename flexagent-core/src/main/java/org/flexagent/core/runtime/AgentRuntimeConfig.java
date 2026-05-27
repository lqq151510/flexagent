package org.flexagent.core.runtime;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

public final class AgentRuntimeConfig {
    private final String type;
    private final Object model;
    private final Map<String, Object> options;

    public AgentRuntimeConfig(String type, Object model, Map<String, Object> options) {
        this.type = Objects.requireNonNull(type, "type must not be null");
        this.model = model;
        this.options = options == null ? Collections.emptyMap() : Map.copyOf(options);
    }

    public String type() {
        return type;
    }

    public Object model() {
        return model;
    }

    public <T> T model(Class<T> modelType) {
        if (model == null) {
            return null;
        }
        return modelType.cast(model);
    }

    public Map<String, Object> options() {
        return options;
    }

    @SuppressWarnings("unchecked")
    public <T> T option(String key, Class<T> valueType) {
        Object value = options.get(key);
        if (value == null) {
            return null;
        }
        return (T) valueType.cast(value);
    }
}
