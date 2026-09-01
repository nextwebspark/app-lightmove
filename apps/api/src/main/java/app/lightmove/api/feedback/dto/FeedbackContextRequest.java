package app.lightmove.api.feedback.dto;

import jakarta.validation.constraints.Size;

/**
 * The browser facts the widget collects for itself, as they arrive.
 *
 * <p>Every field is optional and every field is a claim. The sizes are structural bounds so a caller
 * cannot pad an issue body to a megabyte with a user-agent string; nothing here is believed, and all
 * of it is escaped before it reaches the tracker.
 */
public record FeedbackContextRequest(
        @Size(max = 2000) String pageUrl,
        @Size(max = 512) String userAgent,
        @Size(max = 64) String viewport,
        @Size(max = 64) String screenSize,
        @Size(max = 16) String devicePixelRatio,
        @Size(max = 64) String language,
        @Size(max = 64) String timezone,
        @Size(max = 16) String theme,
        @Size(max = 64) String reportedAt
) {}
