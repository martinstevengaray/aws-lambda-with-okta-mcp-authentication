package com.mgaray.jira;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Atlassian Document Format <-> plain text, ported from claude-skills jira-fmt.py adf_text().
// LLM consumers want text; anyone needing raw ADF can hit the REST API directly.
public class Adf {

    private static final Set<String> BLOCK_NODES =
            Set.of("paragraph", "heading", "listItem", "blockquote", "codeBlock");

    public static String toText(Object node) {
        StringBuilder out = new StringBuilder();
        append(out, node);
        return out.toString().strip();
    }

    private static void append(StringBuilder out, Object node) {
        if (node instanceof List<?> children) {
            for (Object child : children) {
                append(out, child);
            }
        } else if (node instanceof Map<?, ?> map) {
            if (map.get("text") instanceof String text) {
                out.append(text);
            }
            append(out, map.get("content"));
            if (BLOCK_NODES.contains(map.get("type"))) {
                out.append("\n");
            }
        }
    }

    public static Map<String, Object> fromText(String text) {
        List<Map<String, Object>> paragraphs = new ArrayList<>();
        for (String paragraph : text.split("\n\n")) {
            paragraphs.add(Map.of(
                    "type", "paragraph",
                    "content", paragraph.isEmpty()
                            ? List.of()
                            : List.of(Map.of("type", "text", "text", paragraph))));
        }
        return Map.of("type", "doc", "version", 1, "content", paragraphs);
    }

}
