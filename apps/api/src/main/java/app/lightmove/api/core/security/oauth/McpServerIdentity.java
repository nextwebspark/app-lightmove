package app.lightmove.api.core.security.oauth;

/**
 * Who issues MCP tokens and for what: the issuer is the deployment's origin, and the resource — every token's
 * audience, the only {@code resource} the server accepts — is the MCP endpoint under it.
 */
public record McpServerIdentity(String issuer, String resourceUrl) {}
