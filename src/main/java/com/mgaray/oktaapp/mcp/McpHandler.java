package com.mgaray.oktaapp.mcp;

import com.mgaray.oktaapp.mcp.Models.ToolDefinition;
import com.mgaray.oktaapp.common.HttpUtils;
import com.mgaray.oktaapp.common.JsonUtils;
import com.mgaray.oktaapp.mcp.jira.JiraClient;
import com.mgaray.oktaapp.mcp.jira.JiraException;
import com.mgaray.oktaapp.mcp.jira.tools.ListMyIssuesTool;
import com.mgaray.oktaapp.mcp.jira.tools.AddCommentTool;
import com.mgaray.oktaapp.mcp.jira.tools.CreateIssueTool;
import com.mgaray.oktaapp.mcp.jira.tools.GetIssueTool;
import com.mgaray.oktaapp.mcp.jira.tools.SearchIssuesTool;
import com.mgaray.oktaapp.mcp.jira.tools.TransitionIssueTool;
import com.okta.jwt.Jwt;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A stateless, hand-rolled Model Context Protocol server over the Streamable
 * HTTP transport. Each Lambda invocation carries a single JSON-RPC request in
 * the body; we dispatch it and return a single JSON-RPC response. No SSE stream
 * and no session id are used (the tools are simple request/response calls).
 */
public class McpHandler {

    private static final String DEFAULT_PROTOCOL_VERSION = "2025-06-18";

    private static final int PARSE_ERROR_CODE = -32700;
    private static final int INVALID_REQUEST_CODE = -32600;
    private static final int METHOD_NOT_FOUND_CODE = -32601;
    //private static final int INVALID_PARAMETER_CODE = -32602;
    private static final int INTERNAL_ERROR_CODE = -32603;

    private final Map<String, ITool> toolMap = new LinkedHashMap<>();
    private final List<ToolDefinition> tools = new ArrayList<>();

    public McpHandler(JiraClient jiraClient) {
        addTool(new ListMyIssuesTool(jiraClient),
                new SearchIssuesTool(jiraClient),
                new GetIssueTool(jiraClient),
                new CreateIssueTool(jiraClient),
                new AddCommentTool(jiraClient),
                new TransitionIssueTool(jiraClient));
    }

    private void addTool(ITool... itools) {
        for (ITool iTool : itools) {
            String name = iTool.toolDefinition().name();
            if (toolMap.containsKey(name)) {
                throw new IllegalArgumentException("duplicate tool name: " + name);
            }
            toolMap.put(name, iTool);
            tools.add(iTool.toolDefinition());
        }
    }

    public Map<String, Object> handle(Map<String, Object> event, Jwt jwt) {
        Map<String, Object> request;
        try {
            request = HttpUtils.parseBase64EncodedBody(event);
        } catch (Exception e) {
            return rpcError(null, PARSE_ERROR_CODE, "Parse error");
        }
        Object id = request.get("id");
        String method = request.get("method") instanceof String s ? s : null;
        if (method == null) {
            return rpcError(id, INVALID_REQUEST_CODE, "Invalid Request: missing method");
        }
        if (method.startsWith("notifications/")) { // Notifications expect no JSON-RPC reply.
            return HttpUtils.responseJson(202, "");
        }
        try {
            return switch (method) {
                case "initialize" -> rpcResult(id, initialize(request));
                case "ping" -> rpcResult(id, Map.of());
                case "tools/list" -> rpcResult(id, Map.of("tools", tools));
                case "tools/call" -> rpcResult(id, callTool(request));
                default -> rpcError(id, METHOD_NOT_FOUND_CODE, "Method not found: " + method);
            };
        } catch (Exception e) {
            return rpcError(id, INTERNAL_ERROR_CODE, "Internal error: " + e.getMessage());
        }
    }

    private Map<String, Object> initialize(Map<String, Object> request) {
        String protocolVersion = JsonUtils.getNestedField(request, "params", "protocolVersion");
        return Map.of(
                "protocolVersion", protocolVersion != null ? protocolVersion : DEFAULT_PROTOCOL_VERSION,
                "capabilities", Map.of("tools", Map.of()),
                "serverInfo", Map.of("name", "jira-mcp-server", "version", "1.0.0"));
    }

    private Map<String, Object> callTool(Map<String, Object> request) {
        String name = JsonUtils.getNestedField(request, "params", "name");
        Map<String, Object> args = JsonUtils.getNestedMap(request, "params", "arguments");
        try {
            ITool tool = toolMap.get(name);
            if (tool == null) {
                throw new IllegalArgumentException("Unknown tool: " + name);
            }
            return tool.callTool(args);
        } catch (JiraException | IllegalArgumentException e) {
            //respond with rpcResult with error content, not rpcError
            Map<String, Object> textContent = Map.of("type", "text", "text", "Error: " + e.getMessage());
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("content", List.of(textContent));
            result.put("isError", true);
            return result;
        }
    }

    // ---- JSON-RPC envelope helpers ----
    private static Map<String, Object> rpcResult(Object id, Object result) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("jsonrpc", "2.0");
        body.put("id", id);
        body.put("result", result);
        return HttpUtils.responseJson(200, JsonUtils.toJson(body));
    }

    private static Map<String, Object> rpcError(Object id, int code, String message) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", code);
        error.put("message", message);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("jsonrpc", "2.0");
        body.put("id", id);
        body.put("error", error);
        return HttpUtils.responseJson(200, JsonUtils.toJson(body));
    }

}
