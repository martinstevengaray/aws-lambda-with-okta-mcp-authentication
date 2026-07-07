package com.mgaray.oktaapp;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.mgaray.jira.JiraHttpClient;
import com.mgaray.jira.JiraTools;
import com.mgaray.mcp.McpHandler;
import com.mgaray.oktaapp.common.AwsServicesDelegate;
import com.mgaray.oktaapp.common.HttpUtils;
import com.mgaray.oktaapp.common.JsonUtils;
import com.okta.jwt.Jwt;
import com.okta.jwt.JwtVerificationException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class OktaAppLambda implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    private static final String MCP_PATH = "/mcp";

    private final OktaDelegate oktaDelegate;
    private final McpHandler mcpHandler;

    public OktaAppLambda() throws Exception {
        this(buildOktaDelegate(), buildMcpHandler());
    }

    OktaAppLambda(OktaDelegate oktaDelegate, McpHandler mcpHandler) {
        this.oktaDelegate = oktaDelegate;
        this.mcpHandler = mcpHandler;
    }

    private static OktaDelegate buildOktaDelegate() {
        String oktaIssuer = System.getenv("OKTA_ISSUER");
        String oktaAudience = System.getenv("OKTA_AUDIENCE");
        String oktaWebClientId = System.getenv("OKTA_WEB_CLIENT_ID");
        String oktaScopes = System.getenv("OKTA_SCOPES");
        String oktaWebClientSecretSsmParameterKey = System.getenv("OKTA_WEB_CLIENT_SECRET_SSM_PARAMETER_KEY");
        String oktaWebClientSecret = AwsServicesDelegate.fetchSmmParameterValue(oktaWebClientSecretSsmParameterKey);
        return new OktaDelegate(oktaIssuer, oktaAudience, oktaWebClientId, oktaWebClientSecret, oktaScopes);
    }

    private static McpHandler buildMcpHandler() {
        String jiraCloudId = System.getenv("JIRA_CLOUDID");
        String mcpWriteScope = System.getenv("MCP_WRITE_SCOPE");
        // No cloud id means JIRA is not configured: serve MCP with an empty tool list.
        if (jiraCloudId == null || jiraCloudId.isBlank()) {
            return new McpHandler(List.of(), mcpWriteScope);
        }
        String jiraEmail = System.getenv("JIRA_EMAIL");
        String jiraApiToken = AwsServicesDelegate.fetchSmmParameterValue(
                System.getenv("JIRA_API_TOKEN_SSM_PARAMETER_KEY"));
        JiraHttpClient jiraClient = new JiraHttpClient(
                JiraHttpClient.baseUrlFor(jiraCloudId), jiraEmail, jiraApiToken,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        return new McpHandler(JiraTools.all(jiraClient), mcpWriteScope);
    }

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> event, Context context) {
        String path = JsonUtils.getNestedField(event, "requestContext", "http", "path");
        if (MCP_PATH.equals(path)) {
            return handleMcpRequest(event, context);
        }
        Jwt jwt;
        try {
            jwt = oktaDelegate.readJwt(event);
            return createSuccessResponse(event, jwt, context);
        } catch (JwtVerificationException e) {
            return oktaDelegate.authenticationRedirects(event, context);
        }
    }

    // MCP clients are programmatic: reject bad tokens with 401 instead of the browser redirect flow.
    private Map<String, Object> handleMcpRequest(Map<String, Object> event, Context context) {
        try {
            Jwt jwt = oktaDelegate.readJwt(event);
            return mcpHandler.handle(event, jwt, context);
        } catch (JwtVerificationException e) {
            return HttpUtils.response(401,
                    Map.of("content-type", "application/json",
                            "www-authenticate", "Bearer realm=\"mcp\", error=\"invalid_token\""),
                    JsonUtils.toString(Map.of(
                            "error", "invalid_token",
                            "error_description", "valid Okta bearer token required")));
        }
    }

    private Map<String, Object> createSuccessResponse(Map<String, Object> event, Jwt jwt, Context context) {
        Map<String, Object> response = new LinkedHashMap<>();
        Map<String, Object> http = JsonUtils.getNestedMap(event, "requestContext", "http");
        Map<String, Object> headers = JsonUtils.getNestedMap(event,  "headers");
        response.put("method", http.get("method"));
        response.put("path", http.get("path"));
        response.put("sourceIp", http.get("sourceIp"));
        response.put("userAgent", http.get("userAgent"));
        response.put("queryStringParameters", event.get("queryStringParameters"));
        response.put("headers", headers);
        response.put("body", event.get("body"));
        response.put("requestId", context.getAwsRequestId());
        response.put("jwtClaims", jwt.getClaims());
        return HttpUtils.response(200, Map.of("content-type", "application/json"),
                JsonUtils.toString(response));
    }

}
