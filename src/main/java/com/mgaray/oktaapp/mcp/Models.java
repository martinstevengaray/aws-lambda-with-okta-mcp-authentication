package com.mgaray.oktaapp.mcp;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

public class Models {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ToolDefinition(String name,
                                 String title,
                                 String description,
                                 JsonSchema inputSchema,
                                 JsonSchema outputSchema,
                                 Annotations annotations) {
        public ToolDefinition(String name, String description, JsonSchema inputSchema) {
            this(name, null, description, inputSchema, null, null);
        }
        public ToolDefinition(String name, String description, JsonSchema inputSchema, JsonSchema outputSchema) {
            this(name, null, description, inputSchema, outputSchema, null);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Annotations(String title,
                              Boolean readOnlyHint,
                              Boolean destructiveHint,
                              Boolean idempotentHint,
                              Boolean openWorldHint) {}


    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record JsonSchema(String type, String description, JsonSchema items,
                             Map<String, JsonSchema> properties, List<String> required) {
        private static final String objectType = "object";
        private static final String stringType = "string";
        private static final String integerType = "integer";
        private static final String arrayType = "array";
        public static JsonSchema string(String description) {
            return new JsonSchema(stringType, description, null, null, null);
        }

        public static JsonSchema integer(String description) {
            return new JsonSchema(integerType, description, null, null, null);
        }

        public static JsonSchema array(String description, JsonSchema items) {
            return new JsonSchema(arrayType, description, items, null, null);
        }

        public static JsonSchema object(String description,
                                        Map<String, JsonSchema> properties,
                                        List<String> required) {
            return new JsonSchema(objectType, description, null, properties, required);
        }

        public static JsonSchema object(Map<String, JsonSchema> properties,
                                        List<String> required) {
            return new JsonSchema(objectType, null, null, properties, required);
        }
    }

}
