package app.lightmove.api.core.llm.model;

import java.util.Map;
import java.util.regex.Pattern;

/** Placeholder → original text, for re-hydrating what {@link app.lightmove.api.core.llm.service.TextPseudonymiser} redacted. */
public record Pseudonyms(Map<String, String> placeholderToOriginal) {

    /**
     * What a literal {@code "[["} / {@code "]]"} in the source is escaped to before minting. Without it a
     * hostile document containing {@code "[[COMPANY_1]]"} would be re-hydrated into the client's real
     * name, reading its tenant's secret out of the redactor.
     */
    public static final String ESCAPED_OPEN = "⟦";
    public static final String ESCAPED_CLOSE = "⟧";

    /** Generic, not this map's own placeholders: a model can invent one it was never given. */
    private static final Pattern PLACEHOLDER_SHAPE = Pattern.compile("\\[\\[[A-Z]+_\\d+]]");

    public Pseudonyms {
        placeholderToOriginal = Map.copyOf(placeholderToOriginal);
    }

    /** Restores every minted placeholder, then un-escapes the source's own brackets. */
    public String rehydrate(String text) {
        if (text == null) {
            return null;
        }
        String rehydrated = text;
        for (Map.Entry<String, String> entry : placeholderToOriginal.entrySet()) {
            rehydrated = rehydrated.replace(entry.getKey(), entry.getValue());
        }
        return rehydrated.replace(ESCAPED_OPEN, "[[").replace(ESCAPED_CLOSE, "]]");
    }

    /** A placeholder survived re-hydration; such a field is dropped and logged, never surfaced. */
    public boolean hasResidue(String text) {
        return text != null && PLACEHOLDER_SHAPE.matcher(text).find();
    }
}
