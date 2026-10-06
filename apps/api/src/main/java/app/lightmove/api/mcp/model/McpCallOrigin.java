package app.lightmove.api.mcp.model;

/** Where an MCP call came from, carried to its tool for the audit line: the tool may run off the request's thread. */
public record McpCallOrigin(String ipAddress, String userAgent) {

    public static final String CONTEXT_KEY = "lightmove.mcp.origin";
}
