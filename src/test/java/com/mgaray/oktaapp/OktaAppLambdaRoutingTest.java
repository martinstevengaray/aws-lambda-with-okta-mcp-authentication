package com.mgaray.oktaapp;

import com.mgaray.Fixtures;
import com.mgaray.mcp.McpHandler;
import com.mgaray.oktaapp.common.JsonUtils;
import com.okta.jwt.Jwt;
import com.okta.jwt.JwtVerificationException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OktaAppLambdaRoutingTest {

    // OktaDelegate's methods are not final, so a subclass works as a stub verifier.
    static class StubOktaDelegate extends OktaDelegate {
        private final boolean valid;

        StubOktaDelegate(boolean valid) {
            super("https://example.okta.com/oauth2/default", "api://default", "client-id", "secret", "openid");
            this.valid = valid;
        }

        @Override
        public Jwt readJwt(Map<String, Object> event) throws JwtVerificationException {
            if (!valid) {
                throw new JwtVerificationException("invalid token");
            }
            return Fixtures.jwt(Map.of("sub", "user@example.com"));
        }
    }

    private static final McpHandler MCP_HANDLER = new McpHandler(List.of(), "");

    private Map<String, Object> invoke(boolean validToken, String path, String body) {
        OktaAppLambda lambda = new OktaAppLambda(new StubOktaDelegate(validToken), MCP_HANDLER);
        return lambda.handleRequest(
                Fixtures.event("POST", path, Map.of("content-type", "application/json"), body),
                Fixtures.context());
    }

    @Test
    void mcpWithBadTokenReturns401NotRedirect() {
        Map<String, Object> response = invoke(false, "/mcp", "{}");
        assertEquals(401, response.get("statusCode"));
        @SuppressWarnings("unchecked")
        Map<String, String> headers = (Map<String, String>) response.get("headers");
        assertNotNull(headers.get("www-authenticate"));
        assertEquals("invalid_token", JsonUtils.getNestedField((String) response.get("body"), "error"));
    }

    @Test
    void mcpWithGoodTokenReachesJsonRpc() {
        Map<String, Object> response = invoke(true, "/mcp",
                JsonUtils.toString(Map.of("jsonrpc", "2.0", "id", 1, "method", "ping", "params", Map.of())));
        assertEquals(200, response.get("statusCode"));
        assertEquals("2.0", JsonUtils.getNestedField((String) response.get("body"), "jsonrpc"));
    }

    @Test
    void otherPathWithBadTokenStillRedirectsToOkta() {
        Map<String, Object> response = invoke(false, "/", null);
        assertEquals(302, response.get("statusCode"));
        @SuppressWarnings("unchecked")
        Map<String, String> headers = (Map<String, String>) response.get("headers");
        assertTrue(headers.get("location").contains("/v1/authorize"), headers.get("location"));
    }

    @Test
    void otherPathWithGoodTokenKeepsEchoBehavior() {
        Map<String, Object> response = invoke(true, "/whatever", null);
        assertEquals(200, response.get("statusCode"));
        assertEquals("/whatever", JsonUtils.getNestedField((String) response.get("body"), "path"));
    }

}
