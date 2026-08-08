package com.mgaray.oktaapp.mcp;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

public class Models {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ToolDefinition(String name, String title, String description, InputSchema inputSchema,
                                 Annotations annotations) {
        public ToolDefinition(String name, String description, InputSchema inputSchema) {
            this(name, null, description, inputSchema, null);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Annotations(String title, Boolean readOnlyHint, Boolean destructiveHint,
                               Boolean idempotentHint, Boolean openWorldHint) {}

    public record InputSchema(String type, Map<String, PropertyDescription> properties, List<String> required) {
        public InputSchema(Map<String, PropertyDescription> properties, List<String> required) {
            this(objectType, properties, required);
        }
    }
    public record PropertyDescription(String type, String description) {
        public static PropertyDescription string(String description) {
            return new PropertyDescription(stringType, description);
        }
        public static PropertyDescription integer(String description) {
            return new PropertyDescription(integerType, description);
        }
    }
    private static final String objectType = "object";
    private static final String stringType = "string";
    private static final String integerType = "integer";

}
