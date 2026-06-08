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

import org.flexagent.core.util.FlexObjectMapper;

public class ToolAdapter {
    private static final Logger log = LoggerFactory.getLogger(ToolAdapter.class);
    private static final ObjectMapper mapper = FlexObjectMapper.getInstance();

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
            boolean isFlexTool = method.isAnnotationPresent(org.flexagent.core.tool.FlexTool.class);
            boolean isLc4jTool = method.isAnnotationPresent(dev.langchain4j.agent.tool.Tool.class);
            
            if (isFlexTool || isLc4jTool) {
                ToolSpecification spec;
                String toolName;
                if (isFlexTool) {
                    org.flexagent.core.tool.FlexTool flexTool = method.getAnnotation(org.flexagent.core.tool.FlexTool.class);
                    toolName = flexTool.name().isEmpty() ? flexTool.value() : flexTool.name();
                    if (toolName.isEmpty()) {
                        toolName = method.getName();
                    }
                    String description = flexTool.description();
                    
                    // Generate schema and convert to ToolSpecification
                    String schemaJson = org.flexagent.core.tool.ToolSchemaGenerator.generateSchema(method);
                    try {
                        com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>> typeRef = 
                                new com.fasterxml.jackson.core.type.TypeReference<>() {};
                        Map<String, Object> schemaMap = mapper.readValue(schemaJson, typeRef);
                        String schemaType = (String) schemaMap.getOrDefault("type", "object");
                        Map<String, Object> rawProperties = (Map<String, Object>) schemaMap.get("properties");
                        Map<String, Map<String, Object>> properties = new HashMap<>();
                        if (rawProperties != null) {
                            for (Map.Entry<String, Object> entry : rawProperties.entrySet()) {
                                if (entry.getValue() instanceof Map) {
                                    properties.put(entry.getKey(), (Map<String, Object>) entry.getValue());
                                }
                            }
                        }
                        List<String> required = (List<String>) schemaMap.get("required");
                        
                        dev.langchain4j.agent.tool.ToolParameters toolParams = dev.langchain4j.agent.tool.ToolParameters.builder()
                                .type(schemaType)
                                .properties(properties)
                                .required(required != null ? required : Collections.emptyList())
                                .build();
                        
                        spec = ToolSpecification.builder()
                                .name(toolName)
                                .description(description)
                                .parameters(toolParams)
                                .build();
                    } catch (Exception e) {
                        log.error("Failed to build ToolSpecification for FlexTool method: {}", method.getName(), e);
                        continue;
                    }
                    log.info("Registered FlexTool: {} from class {}", toolName, obj.getClass().getSimpleName());
                } else {
                    spec = ToolSpecifications.toolSpecificationFrom(method);
                    toolName = spec.name();
                    log.info("Registered LangChain4j Tool: {} from class {}", toolName, obj.getClass().getSimpleName());
                }
                
                method.setAccessible(true);
                registry.put(toolName, new ToolMethodInfo(obj, method, spec));
            }
        }
    }

    public List<ToolDefinition> getTools() {
        List<ToolDefinition> tools = new ArrayList<>();
        for (ToolMethodInfo info : registry.values()) {
            String paramsSchema = "{}";
            if (info.spec.parameters() != null) {
                try {
                    Map<String, Object> map = new HashMap<>();
                    map.put("type", info.spec.parameters().type());
                    map.put("properties", info.spec.parameters().properties());
                    if (info.spec.parameters().required() != null && !info.spec.parameters().required().isEmpty()) {
                        map.put("required", info.spec.parameters().required());
                    }
                    paramsSchema = mapper.writeValueAsString(map);
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
            return org.flexagent.core.runtime.FlexAgentObservationUtils.observeToolInvoke(call.name(), () -> {
                try {
                    Object result = info.method.invoke(info.target, args);
                    return new ToolResult(call.id(), call.name(), result, null);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
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
            org.flexagent.core.tool.FlexParam flexParam = param.getAnnotation(org.flexagent.core.tool.FlexParam.class);
            dev.langchain4j.agent.tool.P pAnn = param.getAnnotation(dev.langchain4j.agent.tool.P.class);
            String name = null;
            if (flexParam != null) {
                name = flexParam.name().isEmpty() ? flexParam.value() : flexParam.name();
            } else if (pAnn != null) {
                name = pAnn.value();
            }
            if (name == null || name.isEmpty()) {
                name = param.getName();
            }
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
