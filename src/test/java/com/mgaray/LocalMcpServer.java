package com.mgaray;

import com.mgaray.jira.JiraHttpClient;
import com.mgaray.jira.JiraTools;
import com.mgaray.mcp.McpHandler;
import com.mgaray.oktaapp.common.JsonUtils;
import com.sun.net.httpserver.HttpServer;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Local MCP server for curl-testing the JIRA tools against the real JIRA API without
// deploying. Lives in test sources on purpose: it skips Okta entirely (fake Jwt), so
// this auth bypass can never ship in the Lambda zip.
//
// Usage:
//   source local/config.sh   # needs JIRA_EMAIL, JIRA_TOKEN, JIRA_CLOUDID
//   ./gradlew runLocal
//   curl -s localhost:8080/mcp -H 'content-type: application/json' -d '{"jsonrpc":"2.0","id":1,"method":"tools/list"}'
public class LocalMcpServer {

    public static void main(String[] args) throws Exception {
        String email = requireEnv("JIRA_EMAIL");
        String token = requireEnv("JIRA_TOKEN");
        String cloudId = requireEnv("JIRA_CLOUDID");
        String writeScope = System.getenv().getOrDefault("MCP_WRITE_SCOPE", "");

        JiraHttpClient jiraClient = new JiraHttpClient(
                JiraHttpClient.baseUrlFor(cloudId), email, token,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        McpHandler mcpHandler = new McpHandler(JiraTools.all(jiraClient), writeScope);

        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/mcp", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Map<String, String> headers = new LinkedHashMap<>();
            exchange.getRequestHeaders().forEach((name, values) ->
                    headers.put(name.toLowerCase(), String.join(",", values)));
            Map<String, Object> event = Fixtures.event(
                    exchange.getRequestMethod(), "/mcp", headers, body);
            JsonUtils.getNestedMap(event, "requestContext").put("domainName", "localhost");

            Map<String, Object> response = mcpHandler.handle(event,
                    Fixtures.jwt(Map.of("sub", email, "scp", List.of(writeScope))),
                    Fixtures.context());

            if (response.get("headers") instanceof Map<?, ?> responseHeaders) {
                responseHeaders.forEach((name, value) ->
                        exchange.getResponseHeaders().set(String.valueOf(name), String.valueOf(value)));
            }
            byte[] responseBody = String.valueOf(response.getOrDefault("body", ""))
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders((Integer) response.get("statusCode"),
                    responseBody.length == 0 ? -1 : responseBody.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(responseBody);
            }
        });
        server.start();
        System.out.println("Local MCP server (no auth) on http://localhost:" + port + "/mcp"
                + " — JIRA cloud " + cloudId + " as " + email);
    }

    private static String requireEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            System.err.println("Missing env var " + name + " — run: source local/config.sh");
            System.exit(1);
        }
        return value;
    }

}
