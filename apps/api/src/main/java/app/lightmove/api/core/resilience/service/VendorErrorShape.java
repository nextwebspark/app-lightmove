package app.lightmove.api.core.resilience.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import tools.jackson.databind.JsonNode;

/**
 * What a refusal with no code looked like — empty, not JSON, or JSON with these field names — so a 411 from a
 * gateway's page still reads as what it was. Only well-known error field names are spelled out: some vendors key
 * objects by data (a person's name, an id), so any other name prints as {@code ?}.
 */
final class VendorErrorShape {

    private static final Set<String> KNOWN_FIELDS = Set.of("error", "errors", "message", "code", "status", "type",
            "detail", "title", "error_description", "error_codes", "innerError", "details");
    private static final int MAX_FIELD_NAMES = 8;

    private VendorErrorShape() {
    }

    static String of(byte[] body, JsonNode json) {
        if (body == null || body.length == 0) {
            return "empty body";
        }
        if (json == null) {
            return "not JSON, " + body.length + " bytes";
        }
        String shape = "JSON fields " + fieldNamesOf(json);
        JsonNode error = json.path("error");
        return error.isObject() ? shape + ", error fields " + fieldNamesOf(error) : shape;
    }

    private static List<String> fieldNamesOf(JsonNode node) {
        List<String> names = new ArrayList<>();
        for (String name : node.propertyNames()) {
            if (names.size() == MAX_FIELD_NAMES) {
                break;
            }
            names.add(KNOWN_FIELDS.contains(name) ? name : "?");
        }
        return names;
    }
}
