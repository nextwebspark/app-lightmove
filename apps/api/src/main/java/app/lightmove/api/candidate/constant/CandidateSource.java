package app.lightmove.api.candidate.constant;

import app.lightmove.api.common.constant.ApiValueEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

/**
 * Which door a profile came through — provenance the reader is entitled to see, since a compensation
 * figure taken from the executive and one a bulk CSV asserted are not equally trustworthy.
 *
 * <p>{@link #CSV} is self-asserted rather than proven: it travels on the wire like the others and the
 * import is simply the only thing that sends it, so it is a label and never evidence.
 */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum CandidateSource implements ApiValueEnum {

    /** Typed in on the Companies screen. */
    MANUAL("manual"),

    /** Bulk-imported from a spreadsheet a consultant uploaded. */
    CSV("csv"),

    /** Read off a live profile page by the browser plugin, and the capture enrichment researches. */
    EXTENSION("extension"),

    /**
     * Found by a Find executives run: a vendor search hit the model picked. Like {@link #CSV} it is a
     * label and never evidence — the profile came from a stored dataset, not from anybody who met them.
     */
    AI_SOURCED("ai_sourced"),

    /** Ticked on a page of Strategy's People mode: the search hit is the research, like {@link #AI_SOURCED}. */
    PEOPLE_SEARCH("people_search");

    private final String value;

    public static CandidateSource fromValue(String value) {
        return ApiValueEnum.fromValue(CandidateSource.class, value);
    }
}
