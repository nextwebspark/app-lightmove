package app.lightmove.api.core.text.service;

/**
 * A search box's text as the literal part of a SQL {@code LIKE} pattern, so {@code 50%} or {@code a_b}
 * matches those characters rather than standing for any. The query must say {@code ESCAPE '\'}.
 */
public final class LikePatterns {

    private LikePatterns() {}

    public static String escape(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
