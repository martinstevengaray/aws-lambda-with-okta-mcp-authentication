package com.mgaray.jira;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mgaray.oktaapp.common.JsonUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class JiraHttpClient implements JiraClient {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final String baseUrl;
    private final String basicAuth;
    private final HttpClient httpClient;

    // Production baseUrl: https://api.atlassian.com/ex/jira/<cloudId>/rest/api/3
    public JiraHttpClient(String baseUrl, String email, String apiToken, HttpClient httpClient) {
        this.baseUrl = baseUrl;
        this.basicAuth = "Basic " + Base64.getEncoder().encodeToString(
                (email + ":" + apiToken).getBytes(StandardCharsets.UTF_8));
        this.httpClient = httpClient;
    }

    public static String baseUrlFor(String cloudId) {
        return "https://api.atlassian.com/ex/jira/" + cloudId + "/rest/api/3";
    }

    @Override
    public Map<String, Object> get(String pathAndQuery) throws JiraApiException {
        return send(request(pathAndQuery).GET().build());
    }

    @Override
    public Map<String, Object> post(String path, Map<String, Object> body) throws JiraApiException {
        return send(request(path)
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(JsonUtils.toString(body)))
                .build());
    }

    private HttpRequest.Builder request(String pathAndQuery) {
        return HttpRequest.newBuilder(URI.create(baseUrl + pathAndQuery))
                .header("authorization", basicAuth)
                .header("accept", "application/json");
    }

    private Map<String, Object> send(HttpRequest request) throws JiraApiException {
        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            throw new JiraApiException("JIRA request failed: " + e.getMessage(), e);
        }
        if (response.statusCode() / 100 != 2) {
            throw new JiraApiException("JIRA API error (HTTP " + response.statusCode() + "): "
                    + errorSummary(response.body()));
        }
        if (response.body() == null || response.body().isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(response.body(), new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new JiraApiException("JIRA returned unparseable JSON: " + e.getMessage(), e);
        }
    }

    // Surface JIRA's errorMessages/errors fields when the body is parseable, like jira-fmt.py does.
    private String errorSummary(String body) {
        if (body == null || body.isBlank()) {
            return "(empty response body)";
        }
        try {
            Map<String, Object> parsed = objectMapper.readValue(body, new TypeReference<Map<String, Object>>() {});
            StringBuilder summary = new StringBuilder();
            if (parsed.get("errorMessages") instanceof List<?> messages && !messages.isEmpty()) {
                summary.append(messages.stream().map(String::valueOf).collect(Collectors.joining("; ")));
            }
            if (parsed.get("errors") instanceof Map<?, ?> errors && !errors.isEmpty()) {
                if (!summary.isEmpty()) {
                    summary.append("; ");
                }
                summary.append(errors.entrySet().stream()
                        .map(e -> e.getKey() + ": " + e.getValue())
                        .collect(Collectors.joining("; ")));
            }
            if (!summary.isEmpty()) {
                return summary.toString();
            }
        } catch (Exception ignored) {
            // fall through to raw body
        }
        return body.length() > 500 ? body.substring(0, 500) + "…" : body;
    }

}
