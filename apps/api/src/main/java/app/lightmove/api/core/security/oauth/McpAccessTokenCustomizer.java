package app.lightmove.api.core.security.oauth;

import java.util.ArrayList;
import java.util.List;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;

/**
 * An MCP access token names who, where and for what — {@code sub} the user, {@code wsId} the workspace the consent
 * screen chose, {@code aud} the MCP resource, {@code grant_id} the grant so a revoked one is refused on its next call —
 * and no roles: the MCP server re-reads permissions on every call, as every guard does.
 */
public class McpAccessTokenCustomizer implements OAuth2TokenCustomizer<JwtEncodingContext> {

    public static final String WORKSPACE_CLAIM = "wsId";
    public static final String GRANT_CLAIM = "grant_id";
    public static final String CLIENT_CLAIM = "client_id";

    private final McpServerIdentity identity;

    public McpAccessTokenCustomizer(McpServerIdentity identity) {
        this.identity = identity;
    }

    @Override
    public void customize(JwtEncodingContext context) {
        if (!OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) {
            return;
        }
        OAuth2Authorization authorization = context.getAuthorization();
        if (authorization == null) {
            return;
        }
        String workspaceId = HashingAuthorizationService.workspaceOf(authorization)
                .map(Object::toString)
                .orElseThrow(() -> new IllegalStateException("Grant " + authorization.getId() + " has no workspace"));
        context.getClaims()
                // An ArrayList, not List.of: the claims are stored in the grant through Spring's Jackson modules,
                // whose allowlist refuses the JDK's immutable collections and would fail every refresh.
                .audience(new ArrayList<>(List.of(identity.resourceUrl())))
                .claim(WORKSPACE_CLAIM, workspaceId)
                .claim(GRANT_CLAIM, authorization.getId())
                .claim(CLIENT_CLAIM, context.getRegisteredClient().getClientId());
    }
}
