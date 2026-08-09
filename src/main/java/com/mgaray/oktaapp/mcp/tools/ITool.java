package com.mgaray.oktaapp.mcp.tools;

import com.mgaray.oktaapp.common.JsonUtils;
import com.mgaray.oktaapp.mcp.Models.ToolDefinition;

import java.util.List;
import java.util.Map;

public interface ITool {

    ToolDefinition toolDefinition();
    Map<String, Object> callTool(Map<String, Object> args);

    //helpers

    static int getInt(Map<String, Object> args, String key, int fallback) {
        try {
            return (int)Double.parseDouble(args.get(key).toString()); //parseDouble so 5.0 is acceptable
        } catch(Exception ignored) {
            return fallback;
        }
    }

    static String getString(Map<String, Object> args, String key) {
        Object value = args.get(key);
        if (!(value instanceof String s) || s.isBlank()) {
            throw new IllegalArgumentException("Missing required argument: " + key);
        }
        return s;
    }

    /**
     * For row-shaped results: the text block is the serialized structured content,
     * so the two cannot drift apart.
     */
    static Map<String, Object> structuredContentResult(Object structured) {
        return structuredContentResult(JsonUtils.toJson(structured), structured);
    }

    /**
     * For prose-shaped results: the text block is a purpose-built rendering rather
     * than serialized JSON, because escaping a multi-line description into one
     * quoted string reads worse than the text it came from.
     */
    static Map<String, Object> structuredContentResult(String text, Object structured) {
        return Map.of(
                "content", List.of(textContentResult(text)),
                "structuredContent", structured);
    }

    static Map<String, Object> textContentResult(String text) {
        return Map.of("type", "text", "text", text);
    }

}


    //left for comparison to getInt() only:-------------------------------------
//    static int intArg(Map<String, Object> args, String key, int fallback) {
//        Object value = args.get(key);
//        if (value instanceof Number n) {
//            long v = n.longValue();
//            return v < 1 ? fallback : (int) Math.min(v, Integer.MAX_VALUE);
//        }
//        if (value instanceof String s && !s.isBlank()) {
//            try {
//                return intArg(Map.of(key, Double.valueOf(s.trim())), key, fallback);
//            } catch (NumberFormatException ignored) {
//                // fall through to the default
//            }
//        }
//        return fallback;
//    }

