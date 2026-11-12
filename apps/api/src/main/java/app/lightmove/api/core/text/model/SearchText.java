package app.lightmove.api.core.text.model;

import app.lightmove.api.core.text.service.LikePatterns;
import java.util.Locale;

/**
 * The text of a mandate's one search box, trimmed and never blank: a case-insensitive substring every read
 * matches the same way. {@link #likePattern} is lower-cased and escaped, for a query saying {@code ESCAPE '\'}.
 */
public record SearchText(String text) {

    public SearchText {
        text = text.strip();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("A search is never blank");
        }
    }

    /** Null for a blank or absent box, which narrows nothing. */
    public static SearchText ofOrNull(String supplied) {
        return supplied == null || supplied.isBlank() ? null : new SearchText(supplied);
    }

    public String likePattern() {
        return "%" + LikePatterns.escape(text.toLowerCase(Locale.ROOT)) + "%";
    }
}
