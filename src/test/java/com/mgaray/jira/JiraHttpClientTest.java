package com.mgaray.jira;

import com.mgaray.oktaapp.common.JsonUtils;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JiraHttpClientTest {

    private HttpServer server;
    private JiraHttpClient client;
    private volatile String lastAuthHeader;
    private volatile String lastRequestBody;

    @BeforeEach
    void startStubJira() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            lastAuthHeader = exchange.getRequestHeaders().getFirst("Authorization");
            lastRequestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String path = exchange.getRequestURI().getPath();
            switch (path) {
                case "/myself" -> respond(exchange, 200, JsonUtils.toString(Map.of("accountId", "abc123")));
                case "/no-content" -> respond(exchange, 204, "");
                case "/bad-request" -> respond(exchange, 400, JsonUtils.toString(Map.of(
                        "errorMessages", java.util.List.of("The issue no longer exists."),
                        "errors", Map.of("project", "project is required"))));
                default -> respond(exchange, 404, "not found");
            }
        });
        server.start();
        client = new JiraHttpClient("http://127.0.0.1:" + server.getAddress().getPort(),
                "user@example.com", "secret-token", HttpClient.newHttpClient());
    }

    @AfterEach
    void stopStubJira() {
        server.stop(0);
    }

    private void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    @Test
    void getSendsBasicAuthAndParsesJson() {
        Map<String, Object> response = client.get("/myself");
        assertEquals("abc123", response.get("accountId"));
        String expectedAuth = "Basic " + Base64.getEncoder().encodeToString(
                "user@example.com:secret-token".getBytes(StandardCharsets.UTF_8));
        assertEquals(expectedAuth, lastAuthHeader);
    }

    @Test
    void postSendsJsonBody() {
        Map<String, Object> response = client.post("/no-content", Map.of("fields", Map.of("summary", "hi")));
        assertEquals(Map.of(), response);
        assertEquals(JsonUtils.toString(Map.of("fields", Map.of("summary", "hi"))), lastRequestBody);
    }

    @Test
    void errorBodyIsSurfacedInException() {
        JiraApiException e = assertThrows(JiraApiException.class, () -> client.get("/bad-request"));
        assertTrue(e.getMessage().contains("HTTP 400"), e.getMessage());
        assertTrue(e.getMessage().contains("The issue no longer exists."), e.getMessage());
        assertTrue(e.getMessage().contains("project is required"), e.getMessage());
    }

    @Test
    void connectionFailureIsWrapped() {
        JiraHttpClient unreachable = new JiraHttpClient("http://127.0.0.1:1",
                "user@example.com", "secret-token", HttpClient.newHttpClient());
        assertThrows(JiraApiException.class, () -> unreachable.get("/myself"));
    }

}
