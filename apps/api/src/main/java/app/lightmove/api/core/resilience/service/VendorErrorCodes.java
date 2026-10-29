package app.lightmove.api.core.resilience.service;

import java.util.List;
import java.util.regex.Pattern;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The vendor's own machine code for a refusal — Graph's {@code error.code}, Google's {@code error.status},
 * Nylas's {@code error.type}, an OAuth token endpoint's {@code error} with Entra's {@code AADSTS} number — and
 * nothing else of the body. Only a single identifier-shaped token is kept, so free text (which echoes the query, a researched
 * person's name) never reaches a log through it.
 */
public final class VendorErrorCodes {

    /** The most of an error body anyone reads: a refusal's code sits at its top, and a hostile one may be endless. */
    public static final int MAX_ERROR_BODY_BYTES = 16 * 1024;

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Pattern CODE = Pattern.compile("[A-Za-z][A-Za-z0-9_.:-]{1,79}");
    private static final List<String> CODE_FIELDS = List.of("code", "status", "type");

    private VendorErrorCodes() {
    }

    /** The body read as JSON, or null when it is empty or not JSON. */
    public static JsonNode jsonOrNull(byte[] body) {
        if (body == null || body.length == 0) {
            return null;
        }
        try {
            return JSON.readTree(body);
        } catch (JacksonException unreadable) {
            return null;
        }
    }

    public static String codeOrNull(JsonNode json) {
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
}
