package app.lightmove.api.core.config;

import java.time.Duration;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * The MCP server's OAuth 2.1 authorization server — {@code lightmove.mcp.*}. Switched by {@code lightmove.mcp.enabled}
 * (off unless set), which is read by the conditions on the chains rather than bound here.
 */
public record McpSettings(
        /** The protected resource every token is minted for, its {@code aud}. Blank means {@code <web.base-url>/api/v1/mcp}. */
        @DefaultValue("") String resourceUrl,
        /** Its own signing key, never the session's: a session token must not verify as an MCP token, nor the reverse. */
        @DefaultValue("file:.keys/mcp-private.pem") String privateKeyLocation,
        @DefaultValue("file:.keys/mcp-public.pem") String publicKeyLocation,
        @DefaultValue("1h") Duration accessTokenTtl,
        /** Sliding: every refresh rotates the token and starts this again. */
        @DefaultValue("30d") Duration refreshTokenTtl,
        @DefaultValue("5m") Duration authorizationCodeTtl,
        /** Counted per instance, like every budget the in-memory limiter keeps. */
        @DefaultValue("30") int authorizePerMinutePerIp,
        @DefaultValue("60") int tokenPerMinutePerIp,
        @DefaultValue("30") int tokenPerMinutePerClient
) {

    public String resourceUrlUnder(String webBaseUrl) {
        return resourceUrl.isBlank() ? stripTrailingSlash(webBaseUrl) + "/api/v1/mcp" : resourceUrl;
    }

    public static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
