package app.lightmove.api.candidate.constant;

import app.lightmove.api.common.constant.ApiValueEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

/**
 * The seven seeded statuses (V131's {@code system_key}): the wire keys older callers speak and where an unconfigured
 * automation moves people (V140); any may be retired. {@link #OFF_LIMITS} is about the person, not off-limits companies.
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
