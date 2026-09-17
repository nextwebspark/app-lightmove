package app.lightmove.api.core.llm.model;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * A minted vocabulary of placeholder → original text, and the one operation it exists for:
 * re-hydrating what {@link app.lightmove.api.core.llm.service.TextPseudonymiser} redacted.
 */
public record Pseudonyms(Map<String, String> placeholderToOriginal) {

    /**
     * The sentinel a literal {@code "[["} / {@code "]]"} in the source is escaped to before any
     * placeholder of our own is minted — outside the ASCII range a job description would plausibly
     * type, so it can never collide with real prose. Without this escape, a hostile document
     * containing a real placeholder's exact syntax (say {@code "[[COMPANY_1]]"}) would be
     * indistinguishable from one this pseudonymiser minted, and re-hydration would forge the client's
     * real name back into the model's own answer — a document reading its own tenant's secret out of
     * the redactor.
     */
    public static final String ESCAPED_OPEN = "⟦";
    public static final String ESCAPED_CLOSE = "⟧";

    /**
     * The shape of a minted placeholder ({@code [[LABEL_1]]}), checked generically rather than
     * against only this call's own registered map. A model can answer a token in this shape that it
     * was never given — inventing one, or echoing a genuinely registered one this map still failed to
     * resolve for some other reason — and both are the same failure to a reviewer: a placeholder
     * where a real value belongs.
     */
    private static final Pattern PLACEHOLDER_SHAPE = Pattern.compile("\\[\\[[A-Z]+_\\d+]]");

    public Pseudonyms {
        placeholderToOriginal = Map.copyOf(placeholderToOriginal);
    }

    /**
     * Replaces every placeholder this vocabulary minted with the original text it stood in for, then
     * un-escapes any bracket the source itself contained. Applied to everything the model returns,
     * source snippets included — a reviewer comparing a placeholder against their own document would
     * otherwise learn nothing.
     */
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

    /**
     * Whether a placeholder this vocabulary minted survives un-rehydrated in this text — the sweep
     * that catches a redaction leak. A field answering true here is dropped and logged, never
     * surfaced: a placeholder persisted into a brief's narrative is what makes this feature look
     * broken.
     */
    public boolean hasResidue(String text) {
        return text != null && PLACEHOLDER_SHAPE.matcher(text).find();
    }
}
