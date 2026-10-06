package app.lightmove.api.mcp.constant;

/** How an MCP call proved who it is: an OAuth access token from our authorization server, or an opted-in API key. */
public enum McpCredentialKind {
    OAUTH,
    API_KEY
}
