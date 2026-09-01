package app.lightmove.api.feedback.model;

/**
 * What the tester's browser was doing when they hit the button.
 *
 * <p>Every field is caller-supplied and none of it is trusted for a decision — it is reproduction
 * detail, and it reaches a GitHub issue, so it is escaped rather than believed. The server adds the
 * one fact the browser cannot lie about, its own view of the request, alongside it.
 */
public record FeedbackContext(
        /** Path, search and hash of the page the report was filed from, with credentials stripped. */
        String pageUrl,
        String userAgent,
        String viewport,
        String screenSize,
        String devicePixelRatio,
        String language,
        String timezone,
        String theme,
        /** The browser's clock, which is frequently not ours — useful precisely when they disagree. */
        String reportedAt
) {}
