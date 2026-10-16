package app.uncava.api.candidate.constant;

import app.uncava.api.common.constant.ApiValueEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

/**
 * The seven statuses every workspace's pipeline is seeded with and can never lose (V131's
 * {@code system_key}): the anchors automation moves people between, and the wire keys the plugin, the
 * public API and every timeline line written before the catalog still speak. Everything else a candidate
 * may hold is a workspace's own status, read through {@code CandidatePipelineCatalog}.
 *
 * <p>{@link #OFF_LIMITS} is about the person and is not the same thing as a mandate's off-limits
 * <i>companies</i>, which live on the strategy and bar a company from the search entirely.
 */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum CandidateSystemStatus implements ApiValueEnum {

    IDENTIFIED("identified", "Identified"),

    CONTACTED("contacted", "Contacted"),

    ENGAGED("engaged", "Engaged"),

    INTERESTED("interested", "Interested"),

    NOT_INTERESTED("notInterested", "Not interested"),

    /** Cannot be approached — an off-limits agreement, or a prior placement. */
    OFF_LIMITS("offLimits", "Off-limits"),

    OUT_OF_SCOPE("outOfScope", "Out of scope");

    private final String value;

    /** The seeded label: what a timeline line written before the catalog names the status. */
    private final String seededLabel;

    public static CandidateSystemStatus fromValue(String value) {
        return ApiValueEnum.fromValue(CandidateSystemStatus.class, value);
    }
}
