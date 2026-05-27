package org.flexagent.core.tool;

import org.flexagent.core.model.ToolDefinition;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class FlexToolScanner {

    public static List<ToolDefinition> scan(Object object) {
        List<ToolDefinition> definitions = new ArrayList<>();
        if (object == null) {
            return definitions;
        }

        for (Method method : object.getClass().getDeclaredMethods()) {
            if (method.isAnnotationPresent(FlexTool.class)) {
                FlexTool annotation = method.getAnnotation(FlexTool.class);
                String toolName = annotation.name().isEmpty() ? annotation.value() : annotation.name();
                if (toolName.isEmpty()) {
                    toolName = method.getName();
                }

                String description = annotation.description();
                String inputSchema = ToolSchemaGenerator.generateSchema(method);

                definitions.add(new ToolDefinition(toolName, description, inputSchema));
            }
        }
        return definitions;
    }
}
