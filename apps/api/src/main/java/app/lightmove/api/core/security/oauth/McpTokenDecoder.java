package app.lightmove.api.core.security.oauth;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Verifies an MCP access token: the MCP key, our issuer, and the MCP resource as its audience. Deliberately not a
 * {@code JwtDecoder} bean — a second one would make every chain that looks the session decoder up by type ambiguous —
 * so the MCP chain (#703) mounts it explicitly.
 */
public class McpTokenDecoder {

    private final NimbusJwtDecoder decoder;

    public McpTokenDecoder(NimbusJwtDecoder decoder) {
        this.decoder = decoder;
    }

    public Jwt decode(String token) throws JwtException {
        return decoder.decode(token);
    }
}
