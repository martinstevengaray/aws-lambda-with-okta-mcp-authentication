package com.mgaray.mcp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mgaray.Fixtures;
import com.mgaray.oktaapp.common.JsonUtils;
import com.okta.jwt.Jwt;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpHandlerTest {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final Jwt NO_SCOPES = Fixtures.jwt(Map.of("sub", "user@example.com"));

    private final McpHandler handler = new McpHandler(List.of(new FakeTool()), "");

    static class FakeTool implements McpTool {
        Map<String, Object> lastArguments;

        @Override
        public String name() {
            return "fake_tool";
        }

        @Override
        public String description() {
            return "a fake tool";
        }

        @Override
        public Map<String, Object> inputSchema() {
            return Map.of("type", "object", "properties", Map.of());
        }

        @Override
        public boolean isWrite() {
            return false;
        }

        @Override
        public String call(Map<String, Object> arguments) {
            lastArguments = arguments;
            if (Boolean.TRUE.equals(arguments.get("explode"))) {
                throw new ToolCallException("kaboom");
            }
            return "fake result";
        }
    }

    private Map<String, Object> post(String body) {
        return handler.handle(mcpEvent("POST", Map.of(), body), NO_SCOPES, Fixtures.context());
    }

    private Map<String, Object> mcpEvent(String method, Map<String, String> extraHeaders, String body) {
        Map<String, String> headers = new java.util.LinkedHashMap<>(Map.of("content-type", "application/json"));
        headers.putAll(extraHeaders);
        return Fixtures.event(method, "/mcp", headers, body);
    }

    private static String rpc(Object id, String method, Map<String, Object> params) {
        return JsonUtils.toString(Map.of("jsonrpc", "2.0", "id", id, "method", method, "params", params));
    }

    private static int errorCode(Map<String, Object> body) {
        return JsonUtils.<Integer>getNestedField(body, "error", "code");
    }

    private static Map<String, Object> bodyOf(Map<String, Object> response) {
        try {
            return objectMapper.readValue((String) response.get("body"), new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void initializeReturnsProtocolVersionAndServerInfo() {
        Map<String, Object> response = post(rpc(1, "initialize", Map.of(
                "protocolVersion", "2025-06-18", "capabilities", Map.of(),
                "clientInfo", Map.of("name", "test", "version", "0"))));
        assertEquals(200, response.get("statusCode"));
        Map<String, Object> body = bodyOf(response);
        assertEquals(1, body.get("id"));
        assertEquals("2025-06-18", JsonUtils.getNestedField(body, "result", "protocolVersion"));
        assertEquals("jira-mcp-server", JsonUtils.getNestedField(body, "result", "serverInfo", "name"));
    }

    @Test
    void notificationReturns202WithoutBody() {
        Map<String, Object> response = post(JsonUtils.toString(
                Map.of("jsonrpc", "2.0", "method", "notifications/initialized")));
        assertEquals(202, response.get("statusCode"));
        assertEquals("", response.get("body"));
    }

    @Test
    void pingReturnsEmptyResult() {
        Map<String, Object> body = bodyOf(post(rpc(7, "ping", Map.of())));
        assertEquals(Map.of(), body.get("result"));
    }

    @Test
    void toolsListReturnsToolDefinitions() {
        Map<String, Object> body = bodyOf(post(rpc(2, "tools/list", Map.of())));
        List<Map<String, Object>> tools = JsonUtils.getNestedField(body, "result", "tools");
        assertEquals(1, tools.size());
        assertEquals("fake_tool", tools.get(0).get("name"));
        assertEquals("a fake tool", tools.get(0).get("description"));
        assertEquals("object", JsonUtils.getNestedField(tools.get(0), "inputSchema", "type"));
    }

    @Test
    void toolsCallReturnsTextContent() {
        Map<String, Object> body = bodyOf(post(rpc(3, "tools/call",
                Map.of("name", "fake_tool", "arguments", Map.of("a", 1)))));
        assertEquals(false, JsonUtils.getNestedField(body, "result", "isError"));
        List<Map<String, Object>> content = JsonUtils.getNestedField(body, "result", "content");
        assertEquals("fake result", content.get(0).get("text"));
    }

    @Test
    void toolCallExceptionBecomesIsErrorResult() {
        Map<String, Object> body = bodyOf(post(rpc(4, "tools/call",
                Map.of("name", "fake_tool", "arguments", Map.of("explode", true)))));
        assertEquals(true, JsonUtils.getNestedField(body, "result", "isError"));
        List<Map<String, Object>> content = JsonUtils.getNestedField(body, "result", "content");
        assertEquals("kaboom", content.get(0).get("text"));
    }

    @Test
    void unknownToolIsInvalidParams() {
        Map<String, Object> body = bodyOf(post(rpc(5, "tools/call", Map.of("name", "nope"))));
        assertEquals(-32602, errorCode(body));
    }

    @Test
    void unknownMethodIsMethodNotFound() {
        Map<String, Object> body = bodyOf(post(rpc(6, "resources/list", Map.of())));
        assertEquals(-32601, errorCode(body));
    }

    @Test
    void malformedJsonIsParseError() {
        Map<String, Object> response = post("{not json");
        assertEquals(400, response.get("statusCode"));
        Map<String, Object> body = bodyOf(response);
        assertEquals(-32700, errorCode(body));
        assertNull(body.get("id"));
    }

    @Test
    void batchArrayIsRejected() {
        Map<String, Object> response = post("[" + rpc(1, "ping", Map.of()) + "]");
        assertEquals(400, response.get("statusCode"));
        assertEquals(-32600, errorCode(bodyOf(response)));
    }

    @Test
    void unsupportedProtocolVersionHeaderIsRejected() {
        Map<String, Object> response = handler.handle(
                mcpEvent("POST", Map.of("mcp-protocol-version", "1999-01-01"), rpc(1, "ping", Map.of())),
                NO_SCOPES, Fixtures.context());
        assertEquals(400, response.get("statusCode"));
    }

    @Test
    void supportedProtocolVersionHeaderIsAccepted() {
        Map<String, Object> response = handler.handle(
                mcpEvent("POST", Map.of("mcp-protocol-version", "2025-06-18"), rpc(1, "ping", Map.of())),
                NO_SCOPES, Fixtures.context());
        assertEquals(200, response.get("statusCode"));
    }

    @Test
    void foreignOriginIsRejected() {
        Map<String, Object> response = handler.handle(
                mcpEvent("POST", Map.of("origin", "https://evil.example.com"), rpc(1, "ping", Map.of())),
                NO_SCOPES, Fixtures.context());
        assertEquals(403, response.get("statusCode"));
    }

    @Test
    void ownDomainAndLocalhostOriginsAreAccepted() {
        for (String origin : List.of("https://" + Fixtures.DOMAIN_NAME, "http://localhost:8080")) {
            Map<String, Object> response = handler.handle(
                    mcpEvent("POST", Map.of("origin", origin), rpc(1, "ping", Map.of())),
                    NO_SCOPES, Fixtures.context());
            assertEquals(200, response.get("statusCode"), "origin: " + origin);
        }
    }

    @Test
    void getIsMethodNotAllowed() {
        Map<String, Object> response = handler.handle(
                mcpEvent("GET", Map.of(), null), NO_SCOPES, Fixtures.context());
        assertEquals(405, response.get("statusCode"));
    }

    @Test
    void base64EncodedBodyIsDecoded() {
        Map<String, Object> event = mcpEvent("POST", Map.of(),
                Base64.getEncoder().encodeToString(rpc(9, "ping", Map.of()).getBytes(StandardCharsets.UTF_8)));
        event.put("isBase64Encoded", true);
        Map<String, Object> body = bodyOf(handler.handle(event, NO_SCOPES, Fixtures.context()));
        assertEquals(Map.of(), body.get("result"));
    }

    @Test
    void writeToolRequiresConfiguredScope() {
        McpTool writeTool = new FakeTool() {
            @Override
            public boolean isWrite() {
                return true;
            }
        };
        McpHandler gated = new McpHandler(List.of(writeTool), "jira:write");
        String call = rpc(1, "tools/call", Map.of("name", "fake_tool", "arguments", Map.of()));

        // no scp claim -> denied as an isError tool result
        Map<String, Object> denied = bodyOf(gated.handle(mcpEvent("POST", Map.of(), call),
                NO_SCOPES, Fixtures.context()));
        assertEquals(true, JsonUtils.getNestedField(denied, "result", "isError"));

        // scp as list -> allowed
        Map<String, Object> allowedList = bodyOf(gated.handle(mcpEvent("POST", Map.of(), call),
                Fixtures.jwt(Map.of("scp", List.of("openid", "jira:write"))), Fixtures.context()));
        assertEquals(false, JsonUtils.getNestedField(allowedList, "result", "isError"));

        // scp as space-joined string -> allowed
        Map<String, Object> allowedString = bodyOf(gated.handle(mcpEvent("POST", Map.of(), call),
                Fixtures.jwt(Map.of("scp", "openid jira:write")), Fixtures.context()));
        assertEquals(false, JsonUtils.getNestedField(allowedString, "result", "isError"));
    }

    @Test
    void readToolIgnoresWriteScope() {
        McpHandler gated = new McpHandler(List.of(new FakeTool()), "jira:write");
        Map<String, Object> body = bodyOf(gated.handle(
                mcpEvent("POST", Map.of(), rpc(1, "tools/call", Map.of("name", "fake_tool", "arguments", Map.of()))),
                NO_SCOPES, Fixtures.context()));
        assertEquals(false, JsonUtils.getNestedField(body, "result", "isError"));
    }

    @Test
    void responsesAreJson() {
        Map<String, Object> response = post(rpc(1, "ping", Map.of()));
        @SuppressWarnings("unchecked")
        Map<String, String> headers = (Map<String, String>) response.get("headers");
        assertEquals("application/json", headers.get("content-type"));
        assertTrue(((String) response.get("body")).startsWith("{"));
        assertFalse(((String) response.get("body")).isEmpty());
    }

}
