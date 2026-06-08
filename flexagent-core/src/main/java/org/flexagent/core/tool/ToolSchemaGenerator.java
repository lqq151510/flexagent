package org.flexagent.core.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.flexagent.core.util.FlexObjectMapper;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class ToolSchemaGenerator {
    private static final ObjectMapper mapper = FlexObjectMapper.getInstance();

    public static String generateSchema(Method method) {
        try {
            ObjectNode root = mapper.createObjectNode();
            root.put("type", "object");

            ObjectNode properties = mapper.createObjectNode();
            List<String> requiredList = new ArrayList<>();

            for (Parameter parameter : method.getParameters()) {
                FlexParam flexParam = parameter.getAnnotation(FlexParam.class);
                String paramName = null;
                String paramDesc = "";
                boolean required = true;

                if (flexParam != null) {
                    paramName = flexParam.name().isEmpty() ? flexParam.value() : flexParam.name();
                    paramDesc = flexParam.description();
                    required = flexParam.required();
                }

                if (paramName == null || paramName.isEmpty()) {
                    paramName = parameter.getName();
                }

                ObjectNode paramNode = mapper.createObjectNode();
                String jsonType = toJsonType(parameter.getType());
                paramNode.put("type", jsonType);
                if (!paramDesc.isEmpty()) {
                    paramNode.put("description", paramDesc);
                }

                properties.set(paramName, paramNode);
                if (required) {
                    requiredList.add(paramName);
                }
            }

            root.set("properties", properties);
            if (!requiredList.isEmpty()) {
                ArrayNode requiredArray = mapper.createArrayNode();
                for (String req : requiredList) {
                    requiredArray.add(req);
                }
                root.set("required", requiredArray);
            }

            return mapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate schema for method: " + method.getName(), e);
        }
    }

    private static String toJsonType(Class<?> type) {
        if (type == String.class || type == Character.class || type == char.class || type.isEnum()) {
            return "string";
        }
        if (type == int.class || type == Integer.class || type == long.class || type == Long.class || 
            type == short.class || type == Short.class || type == byte.class || type == Byte.class) {
            return "integer";
        }
        if (type == double.class || type == Double.class || type == float.class || type == Float.class || 
            type == java.math.BigDecimal.class) {
            return "number";
        }
        if (type == boolean.class || type == Boolean.class) {
            return "boolean";
        }
        if (type.isArray() || Collection.class.isAssignableFrom(type)) {
            return "array";
        }
        return "object";
    }
}
