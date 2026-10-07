package app.lightmove.api.mcp.model;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/** An authenticated MCP call. Its authorities are its scopes, informational only: every tool asks the caller itself. */
public class McpCallerAuthentication extends AbstractAuthenticationToken {

    private final McpCaller caller;

    public McpCallerAuthentication(McpCaller caller) {
        super(caller.scopes().stream().map(scope -> new SimpleGrantedAuthority("SCOPE_" + scope.value())).toList());
        this.caller = caller;
        setAuthenticated(true);
    }

    @Override
    public McpCaller getPrincipal() {
        return caller;
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public String getName() {
        return caller.credentialId().toString();
    }
}
