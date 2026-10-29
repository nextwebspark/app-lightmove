package app.lightmove.api.core.resilience.service;

import java.util.List;
import java.util.regex.Pattern;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The vendor's own machine code for a refusal — Graph's {@code error.code}, Google's {@code error.status}, Nylas's
 * {@code error.type} — and nothing else of the body. Only a single identifier-shaped token is kept, so free text
 * (which echoes the query, a researched person's name) never reaches a log through it.
 */
final class VendorErrorCodes {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Pattern CODE = Pattern.compile("[A-Za-z][A-Za-z0-9_.:-]{1,79}");
    private static final List<String> CODE_FIELDS = List.of("code", "status", "type");

    private VendorErrorCodes() {
    }

    static String codeOrNull(byte[] body) {
        if (body == null || body.length == 0) {
            return null;
        }
        JsonNode error;
        try {
            error = JSON.readTree(body).path("error");
        } catch (JacksonException unreadable) {
            return null;
        }
        for (String field : CODE_FIELDS) {
            JsonNode value = error.get(field);
            if (value != null && value.isString() && CODE.matcher(value.asString()).matches()) {
                return value.asString();
            }
        }
        return null;
    }
}
