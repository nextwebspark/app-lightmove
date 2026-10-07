package app.lightmove.api.mcp.dto;

import app.lightmove.api.publicapi.dto.PublicCandidate;
import app.lightmove.api.publicapi.dto.PublicCompany;
import org.jspecify.annotations.Nullable;

/**
 * Free text written by people or providers, held to a length before it reaches a model: it is data in a field, never
 * an instruction, and a field that cannot run on cannot carry a long one. Applied to what is sent, never to what is
 * stored.
 */
final class McpFreeText {

    static final int MAX_PROSE = 2000;
    static final int MAX_LINE = 200;

    private McpFreeText() {
    }

    /** Cut on a character, never inside a surrogate pair, so an emoji is never left half. */
    static @Nullable String capped(@Nullable String text, int max) {
        if (text == null || text.length() <= max) {
            return text;
        }
        int end = Character.isHighSurrogate(text.charAt(max - 2)) ? max - 2 : max - 1;
        return text.substring(0, end) + "…";
    }

    static PublicCompany capped(PublicCompany company) {
        return company.withDescription(text -> capped(text, MAX_PROSE));
    }

    static PublicCandidate capped(PublicCandidate candidate) {
        return candidate.withFreeText(text -> capped(text, MAX_PROSE), text -> capped(text, MAX_LINE));
    }
}
