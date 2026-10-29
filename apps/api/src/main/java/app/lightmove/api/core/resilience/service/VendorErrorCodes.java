package app.lightmove.api.core.resilience.service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The vendor's own machine code for a refusal — Graph's {@code error.code}, Google's {@code error.status}, Nylas's
 * {@code error.type}, an OAuth token endpoint's {@code error} with Entra's {@code AADSTS} number — and nothing else of
 * the body. Only a single identifier-shaped token is kept, so free text
 * (which echoes the query, a researched person's name) never reaches a log through it. A body with no such code is
 * described by its shape alone: its field names, never their values.
 */
public final class VendorErrorCodes {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Pattern CODE = Pattern.compile("[A-Za-z][A-Za-z0-9_.:-]{1,79}");
    private static final List<String> CODE_FIELDS = List.of("code", "status", "type");
    private static final int MAX_FIELD_NAMES = 8;

    private VendorErrorCodes() {
    }

    public static String codeOrNull(byte[] body) {
        JsonNode json = jsonOrNull(body);
        if (json == null) {
            return null;
        }
        JsonNode error = json.path("error");
        if (isCode(error)) {
            return withEntraCode(error.asString(), json.path("error_codes"));
        }
        for (String field : CODE_FIELDS) {
            JsonNode value = error.get(field);
            if (isCode(value)) {
                return value.asString();
            }
        }
        return null;
    }

    private static boolean isCode(JsonNode value) {
        return value != null && value.isString() && CODE.matcher(value.asString()).matches();
    }

    /** Entra's token endpoint says which rule refused it by number ({@code AADSTS7000215}), beside the OAuth code. */
    private static String withEntraCode(String oauthCode, JsonNode entraCodes) {
        JsonNode first = entraCodes.path(0);
        return first.isIntegralNumber() ? oauthCode + " AADSTS" + first.asLong() : oauthCode;
    }

    /** What a body with no code looked like: empty, not JSON, or JSON with these field names. */
    static String shapeOf(byte[] body) {
        if (body == null || body.length == 0) {
            return "empty body";
        }
        JsonNode json = jsonOrNull(body);
        if (json == null) {
            return "not JSON, " + body.length + " bytes";
        }
        String shape = "JSON fields " + fieldNamesOf(json);
        JsonNode error = json.path("error");
        return error.isObject() ? shape + ", error fields " + fieldNamesOf(error) : shape;
    }

    private static JsonNode jsonOrNull(byte[] body) {
        if (body == null || body.length == 0) {
            return null;
        }
        try {
            return JSON.readTree(body);
        } catch (JacksonException unreadable) {
            return null;
        }
    }

    private static List<String> fieldNamesOf(JsonNode node) {
        List<String> names = new ArrayList<>();
        for (String name : node.propertyNames()) {
            if (names.size() == MAX_FIELD_NAMES) {
                break;
            }
            names.add(CODE.matcher(name).matches() ? name : "?");
        }
        return names;
    }
}
