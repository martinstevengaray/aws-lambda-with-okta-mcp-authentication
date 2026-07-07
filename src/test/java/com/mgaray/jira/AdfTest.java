package com.mgaray.jira;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AdfTest {

    @Test
    void toTextFlattensNestedDocument() {
        Map<String, Object> doc = Map.of("type", "doc", "version", 1, "content", List.of(
                Map.of("type", "heading", "content", List.of(Map.of("type", "text", "text", "Title"))),
                Map.of("type", "paragraph", "content", List.of(
                        Map.of("type", "text", "text", "Hello "),
                        Map.of("type", "text", "text", "world"))),
                Map.of("type", "bulletList", "content", List.of(
                        Map.of("type", "listItem", "content", List.of(
                                Map.of("type", "paragraph", "content", List.of(
                                        Map.of("type", "text", "text", "item one"))))),
                        Map.of("type", "listItem", "content", List.of(
                                Map.of("type", "paragraph", "content", List.of(
                                        Map.of("type", "text", "text", "item two"))))))),
                Map.of("type", "codeBlock", "content", List.of(
                        Map.of("type", "text", "text", "x = 1")))));
        assertEquals("Title\nHello world\nitem one\n\nitem two\n\nx = 1", Adf.toText(doc));
    }

    @Test
    void toTextHandlesNullAndEmpty() {
        assertEquals("", Adf.toText(null));
        assertEquals("", Adf.toText(Map.of("type", "doc", "content", List.of())));
    }

    @Test
    void fromTextBuildsParagraphsAndRoundTrips() {
        Map<String, Object> doc = Adf.fromText("first paragraph\n\nsecond paragraph");
        assertEquals("doc", doc.get("type"));
        assertEquals(1, doc.get("version"));
        List<?> content = (List<?>) doc.get("content");
        assertEquals(2, content.size());
        assertEquals("first paragraph\nsecond paragraph", Adf.toText(doc));
    }

    @Test
    void fromTextHandlesEmptyString() {
        Map<String, Object> doc = Adf.fromText("");
        assertEquals("", Adf.toText(doc));
    }

}
