package com.mgaray.mcp;

import com.amazonaws.services.lambda.runtime.Context;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mgaray.oktaapp.common.HttpUtils;
import com.mgaray.oktaapp.common.JsonUtils;
import com.okta.jwt.Jwt;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

// MCP Streamable HTTP in its simplest legal form: JSON-RPC 2.0 over a single
// POST endpoint, one JSON response per request, no SSE.
public class McpHandler {

    public static final String PROTOCOL_VERSION = "2025-06-18";
    // 2025-06-18 removed JSON-RPC batching, so top-level arrays are rejected.
    private static final Set<String> SUPPORTED_PROTOCOL_VERSIONS = Set.of(PROTOCOL_VERSION, "2025-03-26");
    private static final Map<String, String> JSON_HEADERS = Map.of("content-type", "application/json");

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final Map<String, McpTool> tools;
    private final String requiredWriteScope;

    public McpHandler(List<McpTool> tools, String requiredWriteScope) {
        Map<String, McpTool> toolsByName = new LinkedHashMap<>();
        for (McpTool tool : tools) {
            toolsByName.put(tool.name(), tool);
        }
        this.tools = toolsByName;
        this.requiredWriteScope = (requiredWriteScope == null) ? "" : requiredWriteScope;
    }

    public Map<String, Object> handle(Map<String, Object> event, Jwt jwt, Context context) {
        String method = JsonUtils.getNestedField(event, "requestContext", "http", "method");
        if (!"POST".equalsIgnoreCase(method)) {
            return HttpUtils.response(405, Map.of("allow", "POST", "content-type", "application/json"),
                    JsonUtils.toString(Map.of("error", "method not allowed, MCP requests are POST only")));
        }
        Map<String, Object> rejection = rejectBadOrigin(event);
        if (rejection == null) {
            rejection = rejectBadProtocolVersion(event);
        }
        if (rejection != null) {
            return rejection;
        }

        Object message;
        try {
            message = objectMapper.readValue(readBody(event), Object.class);
        } catch (Exception e) {
            return rpcErrorResponse(400, null, -32700, "parse error: request body is not valid JSON");
        }
        if (message instanceof List) {
            return rpcErrorResponse(400, null, -32600,
                    "batch requests are not supported by MCP protocol " + PROTOCOL_VERSION);
        }
        if (!(message instanceof Map)) {
            return rpcErrorResponse(400, null, -32600, "expected a JSON-RPC request object");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> request = (Map<String, Object>) message;
        // No id means notification (e.g. notifications/initialized): accept, never respond.
        if (!request.containsKey("id")) {
            return HttpUtils.response(202, Map.of(), "");
        }
        Object id = request.get("id");
        String rpcMethod = (request.get("method") instanceof String s) ? s : "";
        Map<String, Object> params = JsonUtils.getNestedMap(request, "params");
        return switch (rpcMethod) {
            case "initialize" -> rpcResultResponse(id, Map.of(
                    "protocolVersion", PROTOCOL_VERSION,
                    "capabilities", Map.of("tools", Map.of()),
                    "serverInfo", Map.of("name", "jira-mcp-server", "version", "1.0.0")));
            case "ping" -> rpcResultResponse(id, Map.of());
            case "tools/list" -> rpcResultResponse(id, Map.of("tools", tools.values().stream()
                    .map(tool -> Map.of(
                            "name", tool.name(),
                            "description", tool.description(),
                            "inputSchema", tool.inputSchema()))
                    .toList()));
            case "tools/call" -> callTool(id, params, jwt, context);
            default -> rpcErrorResponse(200, id, -32601, "method not found: " + rpcMethod);
        };
    }

    private Map<String, Object> callTool(Object id, Map<String, Object> params, Jwt jwt, Context context) {
        McpTool tool = (params.get("name") instanceof String name) ? tools.get(name) : null;
        if (tool == null) {
            return rpcErrorResponse(200, id, -32602, "unknown tool: " + params.get("name"));
        }
        if (tool.isWrite() && !requiredWriteScope.isEmpty() && !hasScope(jwt, requiredWriteScope)) {
            return toolResultResponse(id, "tool " + tool.name()
                    + " requires the '" + requiredWriteScope + "' scope on the bearer token", true);
        }
        Map<String, Object> arguments = JsonUtils.getNestedMap(params, "arguments");
        try {
            return toolResultResponse(id, tool.call(arguments), false);
        } catch (ToolCallException e) {
            return toolResultResponse(id, e.getMessage(), true);
        } catch (RuntimeException e) {
            context.getLogger().log("tool " + tool.name() + " failed: " + e);
            return toolResultResponse(id, "tool " + tool.name() + " failed: " + e.getMessage(), true);
        }
    }

    // Okta emits scp as a JSON array, but tolerate a space-joined string too.
    private boolean hasScope(Jwt jwt, String scope) {
        Object scp = (jwt == null) ? null : jwt.getClaims().get("scp");
        if (scp instanceof List<?> scopes) {
            return scopes.contains(scope);
        }
        if (scp instanceof String s) {
            return List.of(s.split(" ")).contains(scope);
        }
        return false;
    }

    // DNS-rebinding protection required by the MCP spec: browsers send Origin, and it
    // must match this server. Non-browser MCP clients send no Origin and pass through.
    private Map<String, Object> rejectBadOrigin(Map<String, Object> event) {
        String origin = readHeader(event, "origin");
        if (origin == null) {
            return null;
        }
        String originHost;
        try {
            originHost = URI.create(origin).getHost();
        } catch (IllegalArgumentException e) {
            originHost = null;
        }
        String domainName = JsonUtils.getNestedField(event, "requestContext", "domainName");
        if (originHost != null
                && (originHost.equals(domainName) || "localhost".equals(originHost) || "127.0.0.1".equals(originHost))) {
            return null;
        }
        return HttpUtils.response(403, JSON_HEADERS,
                JsonUtils.toString(Map.of("error", "origin not allowed: " + origin)));
    }

    private Map<String, Object> rejectBadProtocolVersion(Map<String, Object> event) {
        String version = readHeader(event, "mcp-protocol-version");
        // Absent header is fine: the spec says to assume 2025-03-26.
        if (version == null || SUPPORTED_PROTOCOL_VERSIONS.contains(version)) {
            return null;
        }
        return HttpUtils.response(400, JSON_HEADERS, JsonUtils.toString(Map.of(
                "error", "unsupported MCP-Protocol-Version: " + version
                        + ", supported: " + SUPPORTED_PROTOCOL_VERSIONS)));
    }

    // Function URL events lowercase header names, but read case-insensitively anyway.
    private String readHeader(Map<String, Object> event, String headerName) {
        for (Map.Entry<String, Object> entry : JsonUtils.getNestedMap(event, "headers").entrySet()) {
            if (headerName.equalsIgnoreCase(entry.getKey()) && entry.getValue() instanceof String s) {
                return s;
            }
        }
        return null;
    }

    private String readBody(Map<String, Object> event) {
        String body = (event.get("body") instanceof String s) ? s : "";
        if (Boolean.TRUE.equals(event.get("isBase64Encoded"))) {
            return new String(Base64.getDecoder().decode(body), StandardCharsets.UTF_8);
        }
        return body;
    }

    private Map<String, Object> toolResultResponse(Object id, String text, boolean isError) {
        return rpcResultResponse(id, Map.of(
                "content", List.of(Map.of("type", "text", "text", text)),
                "isError", isError));
    }

    private Map<String, Object> rpcResultResponse(Object id, Object result) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("jsonrpc", "2.0");
        response.put("id", id);
        response.put("result", result);
        return HttpUtils.response(200, JSON_HEADERS, JsonUtils.toString(response));
    }

    private Map<String, Object> rpcErrorResponse(int httpStatus, Object id, int code, String message) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("jsonrpc", "2.0");
        response.put("id", id);
        response.put("error", Map.of("code", code, "message", message));
        return HttpUtils.response(httpStatus, JSON_HEADERS, JsonUtils.toString(response));
    }

}
