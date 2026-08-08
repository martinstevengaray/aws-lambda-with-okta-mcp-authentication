package com.mgaray.oktaapp.mcp;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

public class Models {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ToolDefinition(String name, String title, String description, InputSchema inputSchema,
                                 InputSchema outputSchema, Annotations annotations) {
        public ToolDefinition(String name, String description, InputSchema inputSchema) {
            this(name, null, description, inputSchema, null, null);
        }

        /**
         * Declaring an outputSchema is a contract, not a hint: the spec requires every
         * result from this tool to carry a {@code structuredContent} object conforming
         * to it. Only use this constructor for tools that actually populate it.
         */
        public ToolDefinition(String name, String description, InputSchema inputSchema, InputSchema outputSchema) {
            this(name, null, description, inputSchema, outputSchema, null);
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
    /**
     * One node of a JSON Schema. Scalars use only {@code type}/{@code description};
     * {@code items} applies to arrays and {@code properties}/{@code required} to nested
     * objects, so the unused fields are null and omitted from the wire format.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PropertyDescription(String type, String description, PropertyDescription items,
                                      Map<String, PropertyDescription> properties, List<String> required) {
        public static PropertyDescription string(String description) {
            return new PropertyDescription(stringType, description, null, null, null);
        }
        public static PropertyDescription integer(String description) {
            return new PropertyDescription(integerType, description, null, null, null);
        }
        /** An array whose every element matches {@code items}. */
        public static PropertyDescription array(String description, PropertyDescription items) {
            return new PropertyDescription(arrayType, description, items, null, null);
        }
        /** A nested object; {@code required} names the properties that must be present. */
        public static PropertyDescription object(String description,
                                                 Map<String, PropertyDescription> properties,
                                                 List<String> required) {
            return new PropertyDescription(objectType, description, null, properties, required);
        }
    }
    private static final String objectType = "object";
    private static final String stringType = "string";
    private static final String integerType = "integer";
    private static final String arrayType = "array";

}
