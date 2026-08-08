package com.mgaray.oktaapp.auth;

import com.mgaray.oktaapp.common.HttpUtils;
import com.mgaray.oktaapp.common.JsonUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The MCP OAuth proxy's tamper-proof {@code state}: carries an MCP client's own loopback redirect_uri
 * (and its original state) through Okta and back, so the proxy knows where to return the authorization
 * code without holding server-side session state.
 *
 * <p>The signature is what makes that safe. The proxy redirects to whatever redirect_uri comes back out
 * of the state, so an attacker who could forge one could redirect an authorization code to a host they
 * control. HMAC verification, together with the proxy's loopback allowlist, is what closes that.
 *
 * <p>Wire format: {@code base64url(JSON{ru, cs}) + "." + base64url(HMAC-SHA256(payload))}.
 */
class SignedState {

    /** Where to send the MCP client back, recovered from a verified state. */
    record ClientReturn(String redirectUri, String state) {}

    private final byte[] signingKey;

    SignedState(String signingKey) {
        this.signingKey = signingKey.getBytes(StandardCharsets.UTF_8);
    }

    String sign(String clientRedirectUri, String clientState) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ru", clientRedirectUri);
        if (clientState != null) {
            payload.put("cs", clientState);
        }
        String encoded = HttpUtils.base64Url(JsonUtils.toJson(payload).getBytes(StandardCharsets.UTF_8));
        return encoded + "." + HttpUtils.base64Url(hmac(encoded));
    }

    /**
     * @throws SecurityException        if the signature does not match — treat as an attack, not a bug
     * @throws IllegalArgumentException if the state is missing or malformed
     */
    ClientReturn verify(String state) {
        if (state == null) {
            throw new IllegalArgumentException("missing state");
        }
        int dot = state.lastIndexOf('.');
        if (dot < 0) {
            throw new IllegalArgumentException("malformed state");
        }
        String encoded = state.substring(0, dot);
        byte[] presented = Base64.getUrlDecoder().decode(state.substring(dot + 1));
        // Constant-time: never leak how much of the signature matched.
        if (!MessageDigest.isEqual(hmac(encoded), presented)) {
            throw new SecurityException("bad state signature");
        }
        Map<String, Object> payload = JsonUtils.parse(
                new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8));
        Object ru = payload.get("ru");
        if (!(ru instanceof String redirectUri) || redirectUri.isBlank()) {
            throw new IllegalArgumentException("state missing redirect_uri");
        }
        Object cs = payload.get("cs");
        return new ClientReturn(redirectUri, cs instanceof String s ? s : null);
    }

    private byte[] hmac(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signingKey, "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC of proxy state failed", e);
        }
    }
}
