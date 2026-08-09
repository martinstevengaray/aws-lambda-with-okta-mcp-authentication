package com.mgaray.oktaapp.mcp.tools;

import com.mgaray.oktaapp.common.JsonUtils;
import com.mgaray.oktaapp.mcp.Models.ToolDefinition;

import java.util.List;
import java.util.Map;

public interface ITool {

    ToolDefinition toolDefinition();
    Map<String, Object> callTool(Map<String, Object> args);

    // ---- Result helpers ----

    /**
     * A tool result carrying both halves the spec expects: the structured object,
     * and the same object serialized into the required text block for clients that
     * ignore {@code structuredContent}. Serializing rather than hand-formatting keeps
     * the two from drifting apart. Only for tools that declare an outputSchema.
     */
    static Map<String, Object> structuredResult(Object structured) {
        return Map.of(
                "content", List.of(textContent(JsonUtils.toJson(structured))),
                "structuredContent", structured);
    }

    static Map<String, Object> textContent(String text) {
        return Map.of("type", "text", "text", text);
    }

    // ---- Argument helpers ----

    /** Reads an optional integer argument, tolerating JSON floats ({@code 5.0}) and strings. */
    static int intArg(Map<String, Object> args, String key, int fallback) {
        Object value = args.get(key);
        if (value instanceof Number n) {
            long v = n.longValue();
            return v < 1 ? fallback : (int) Math.min(v, Integer.MAX_VALUE);
        }
        if (value instanceof String s && !s.isBlank()) {
            try {
                return intArg(Map.of(key, Double.valueOf(s.trim())), key, fallback);
            } catch (NumberFormatException ignored) {
                // fall through to the default
            }
        }
        return fallback;
    }

    /** Reads a required string argument, or reports a tool-level error. */
    static String requiredArg(Map<String, Object> args, String key) {
        Object value = args.get(key);
        if (!(value instanceof String s) || s.isBlank()) {
            throw new IllegalArgumentException("Missing required argument: " + key);
        }
        return s;
    }
}
