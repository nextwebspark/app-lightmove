package app.lightmove.api.position.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

/**
 * What produced a step-one proposal. Mirrors {@code dataimport}'s {@code MappingSource}: the panel
 * says which, because the two are worth different amounts of scrutiny — a reading straight from the
 * model needs a glance, one the heuristic fell back to needs reading.
 */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum ExtractionSource {

    /** Gemini read the document. */
    MODEL("model"),

    /**
     * {@link app.lightmove.api.position.service.HeuristicBriefReader} answered alone because the
     * model could not be reached, or blocked, or answered out of shape — the ordinary case without
     * Application Default Credentials, and the one worth saying out loud. Also the answer when
     * neither path found anything worth proposing — an empty {@code fields} list still names which
     * reading was tried.
     */
    DOCUMENT_HEADINGS("documentHeadings"),

    /**
     * No reading was possible at all — step two and step four's proposers have no heuristic fallback
     * to fall back to, unlike step one, so a failed, blocked or unresolving model call has nothing
     * else to try and lands here instead of a degraded reading.
     */
    NONE("none");

    private final String value;
}
