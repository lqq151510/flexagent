package org.flexagent.langchain4j;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.flexagent.core.model.ToolResult;
import org.flexagent.core.model.ToolDefinition;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;

public class ToolAdapter {
    private static final Logger log = LoggerFactory.getLogger(ToolAdapter.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private final List<Object> toolObjects = new ArrayList<>();
    private final Map<String, ToolMethodInfo> registry = new HashMap<>();

    public ToolAdapter(List<Object> toolObjects) {
        if (toolObjects != null) {
            for (Object obj : toolObjects) {
                register(obj);
            }
        }
    }

    private void register(Object obj) {
        toolObjects.add(obj);
        for (Method method : obj.getClass().getDeclaredMethods()) {
            if (method.isAnnotationPresent(dev.langchain4j.agent.tool.Tool.class)) {
                ToolSpecification spec = ToolSpecifications.toolSpecificationFrom(method);
                String toolName = spec.name();
                method.setAccessible(true);
                registry.put(toolName, new ToolMethodInfo(obj, method, spec));
                log.info("Registered LangChain4j Tool: {} from class {}", toolName, obj.getClass().getSimpleName());
            }
        }
    }

    public List<ToolDefinition> getTools() {
        List<ToolDefinition> tools = new ArrayList<>();
        for (ToolMethodInfo info : registry.values()) {
            String paramsSchema = "{}";
            if (info.spec.parameters() != null) {
                try {
                    paramsSchema = mapper.writeValueAsString(info.spec.parameters());
                } catch (Exception e) {
                    log.warn("Failed to serialize parameters schema for tool: {}", info.spec.name(), e);
                }
            }

            ToolDefinition tool = new ToolDefinition(
                    info.spec.name(),
                    info.spec.description() != null ? info.spec.description() : "",
                    paramsSchema
            );
            tools.add(tool);
        }
        return tools;
    }

    public ToolResult execute(org.flexagent.core.model.ToolCall call) {
        ToolMethodInfo info = registry.get(call.name());
        if (info == null) {
            return new ToolResult(call.id(), call.name(), null, "Tool not found in registry: " + call.name());
        }

        try {
            Object[] args = bindArguments(info.method, call.arguments());
            Object result = info.method.invoke(info.target, args);
            return new ToolResult(call.id(), call.name(), result, null);
        } catch (Exception e) {
            log.error("Error executing tool: {}", call.name(), e);
            String errMsg = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
            return new ToolResult(call.id(), call.name(), null, "Execution error: " + errMsg);
        }
    }

    private Object[] bindArguments(Method method, Map<String, Object> arguments) {
        Parameter[] parameters = method.getParameters();
        Object[] args = new Object[parameters.length];
        
        for (int i = 0; i < parameters.length; i++) {
            Parameter param = parameters[i];
            dev.langchain4j.agent.tool.P pAnn = param.getAnnotation(dev.langchain4j.agent.tool.P.class);
            String name = (pAnn != null) ? pAnn.value() : param.getName();
            // Find parameter description from spec or fallback to matching by index/position if needed
            // LangChain4j spec doesn't store parameter mapping directly, but we can look up in the JSON arguments map
            // Since -parameters compiler flag is standard, we look up by parameter name first.
            Object rawVal = arguments.get(name);
            if (rawVal == null) {
                // Try case-insensitive lookup
                for (Map.Entry<String, Object> entry : arguments.entrySet()) {
                    if (entry.getKey().equalsIgnoreCase(name)) {
                        rawVal = entry.getValue();
                        break;
                    }
                }
            }

            if (rawVal == null) {
                args[i] = null;
            } else {
                args[i] = mapper.convertValue(rawVal, param.getType());
            }
        }
        return args;
    }

    public List<ToolSpecification> getToolSpecifications() {
        List<ToolSpecification> specs = new ArrayList<>();
        for (ToolMethodInfo info : registry.values()) {
            specs.add(info.spec);
        }
        return specs;
    }

    private record ToolMethodInfo(Object target, Method method, ToolSpecification spec) {}
}
