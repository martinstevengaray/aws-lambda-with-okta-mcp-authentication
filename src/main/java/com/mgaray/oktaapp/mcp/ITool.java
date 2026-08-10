package com.mgaray.oktaapp.mcp;

import com.mgaray.oktaapp.common.JsonUtils;
import com.mgaray.oktaapp.mcp.Models.ToolDefinition;

import java.util.List;
import java.util.Map;

public interface ITool {

    ToolDefinition toolDefinition();
    Map<String, Object> callTool(Map<String, Object> args);


    //-----input format helpers-----------------------------------------------------------------------------------------

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

    static String getString(Map<String, Object> args, String key, String fallback) {
        return args.get(key) instanceof String s ? s : fallback;
    }


    //-----output format helpers----------------------------------------------------------------------------------------

    static Map<String, Object> structuredContentResult(Object structured) {
        return structuredContentResult(JsonUtils.toJson(structured), structured);
    }

    static Map<String, Object> structuredContentResult(String text, Object structured) {
        return Map.of(
                "content", List.of(textContentResult(text)),
                "structuredContent", structured);
    }

    static Map<String, Object> textContentResult(String text) {
        return Map.of("type", "text", "text", text);
    }

}

