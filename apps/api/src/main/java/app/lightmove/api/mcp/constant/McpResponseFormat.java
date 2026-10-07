package app.lightmove.api.mcp.constant;

import app.lightmove.api.mcp.model.McpToolRefusal;
import java.util.Locale;

/** How much a tool sends per row: the fields to pick from, or the whole public record beside them. */
public enum McpResponseFormat {

    CONCISE,
    DETAILED;

    public static McpResponseFormat parse(String value) {
        if (value == null || value.isBlank()) {
            return CONCISE;
        }
        return switch (value.strip().toLowerCase(Locale.ROOT)) {
            case "concise" -> CONCISE;
            case "detailed" -> DETAILED;
            default -> throw new McpToolRefusal("response_format is concise or detailed.");
        };
    }

    public boolean isDetailed() {
        return this == DETAILED;
    }
}
