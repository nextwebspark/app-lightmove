package app.lightmove.api.position.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

/** What produced a proposal; the two readings deserve different scrutiny. */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum ExtractionSource {

    MODEL("model"),

    /** {@link app.lightmove.api.position.service.HeuristicBriefReader} answered alone, even if with nothing. */
    DOCUMENT_HEADINGS("documentHeadings"),

    /** A proposer with no heuristic fallback failed. */
    NONE("none");

    private final String value;
}
