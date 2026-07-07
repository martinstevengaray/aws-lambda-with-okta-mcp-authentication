package com.mgaray;

import com.amazonaws.services.lambda.runtime.ClientContext;
import com.amazonaws.services.lambda.runtime.CognitoIdentity;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.okta.jwt.Jwt;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

// Shared test doubles: synthetic Lambda function-URL events, a stub Jwt and a stub Context.
public class Fixtures {

    public static final String DOMAIN_NAME = "abc123.lambda-url.us-east-1.on.aws";

    public static Map<String, Object> event(String method, String path,
                                            Map<String, String> headers, String body) {
        Map<String, Object> http = new LinkedHashMap<>();
        http.put("method", method);
        http.put("path", path);
        Map<String, Object> requestContext = new LinkedHashMap<>();
        requestContext.put("http", http);
        requestContext.put("domainName", DOMAIN_NAME);
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("requestContext", requestContext);
        event.put("headers", headers);
        if (body != null) {
            event.put("body", body);
        }
        return event;
    }

    public static Jwt jwt(Map<String, Object> claims) {
        return new Jwt() {
            @Override
            public String getTokenValue() {
                return "test-token";
            }

            @Override
            public Instant getIssuedAt() {
                return Instant.now();
            }

            @Override
            public Instant getExpiresAt() {
                return Instant.now().plusSeconds(3600);
            }

            @Override
            public Map<String, Object> getClaims() {
                return claims;
            }
        };
    }

    public static Context context() {
        return new Context() {
            @Override
            public String getAwsRequestId() {
                return "test-request-id";
            }

            @Override
            public String getLogGroupName() {
                return "test-log-group";
            }

            @Override
            public String getLogStreamName() {
                return "test-log-stream";
            }

            @Override
            public String getFunctionName() {
                return "mcp-server-lambda";
            }

            @Override
            public String getFunctionVersion() {
                return "$LATEST";
            }

            @Override
            public String getInvokedFunctionArn() {
                return "arn:aws:lambda:us-east-1:000000000000:function:mcp-server-lambda";
            }

            @Override
            public CognitoIdentity getIdentity() {
                return null;
            }

            @Override
            public ClientContext getClientContext() {
                return null;
            }

            @Override
            public int getRemainingTimeInMillis() {
                return 30_000;
            }

            @Override
            public int getMemoryLimitInMB() {
                return 512;
            }

            @Override
            public LambdaLogger getLogger() {
                return new LambdaLogger() {
                    @Override
                    public void log(String message) {
                        System.out.println(message);
                    }

                    @Override
                    public void log(byte[] message) {
                        System.out.println(new String(message));
                    }
                };
            }
        };
    }

}
