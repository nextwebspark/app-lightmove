package app.lightmove.api.mcp.service;

import app.lightmove.api.mcp.model.McpToolRefusal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** An opaque page cursor: where the next page starts, so a page cut short still reads on from its first left-out row. */
final class McpCursor {

    private static final String VERSION = "v1:";

    private McpCursor() {
    }

    static String at(int offset) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString((VERSION + offset).getBytes(StandardCharsets.US_ASCII));
    }

    static int offsetOf(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return 0;
        }
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(cursor.strip()), StandardCharsets.US_ASCII);
            int offset = decoded.startsWith(VERSION) ? Integer.parseInt(decoded.substring(VERSION.length())) : -1;
            if (offset >= 0) {
                return offset;
            }
        } catch (IllegalArgumentException malformed) {
            // Refused below, alike for every cursor this server did not issue.
        }
        throw new McpToolRefusal("That cursor is not one this server issued. Start again without a cursor.");
    }
}
