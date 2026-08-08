package com.mgaray.oktaapp.auth;

/**
 * Everything the auth package needs from the environment, in one named carrier.
 *
 * <p>These values are near-identical strings — two client ids, a client secret, a signing key — so
 * passing them positionally down several constructors made transposing any two a silent, compile-clean
 * bug that only surfaced as an Okta rejection at runtime. Here they are named once, at the single
 * construction site in the Lambda's cold start.
 *
 * @param issuer               Okta authorization server, e.g. https://example.okta.com/oauth2/default
 * @param audience             expected {@code aud} claim on access tokens
 * @param webClientId          OIDC Web app backing the browser flow (empty disables it)
 * @param webClientSecret      secret for that Web app, pulled from SSM
 * @param scopes               space-separated scopes requested by both flows
 * @param mcpClientId          OIDC Native app handed to MCP clients by the registration shim
 * @param symmetricSigningKey  HMAC key for values that round-trip through third parties
 */
public record OktaConfig(String issuer,
                         String audience,
                         String webClientId,
                         String webClientSecret,
                         String scopes,
                         String mcpClientId,
                         String symmetricSigningKey) {
}
