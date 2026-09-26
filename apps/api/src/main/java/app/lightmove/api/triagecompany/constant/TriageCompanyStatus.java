package app.lightmove.api.triagecompany.constant;

import app.lightmove.api.common.constant.ApiValueEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

/**
 * Where a company stands in a mandate's triage. A company enters the universe from the Strategy
 * screen and moves between these as the team triages it; there is no "untriaged" state, because a
 * company nobody has taken a position on simply has no row.
 */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum TriageCompanyStatus implements ApiValueEnum {

    /** Taken into the mandate's working set. What "Add to Universe" writes. */
    IN_UNIVERSE("inUniverse"),

    /** Promoted: worth mapping people at. */
    SHORTLISTED("shortlisted"),

    /**
     * Ruled out. Kept rather than deleted, so re-running "Add all to Universe" after widening the
     * filter cannot quietly resurrect a company the team already decided against.
     */
    DECLINED("declined");

    private final String value;

    public static TriageCompanyStatus fromValue(String value) {
        return ApiValueEnum.fromValue(TriageCompanyStatus.class, value);
    }

    /** Omitted means the landing stage — where a company arrives from Strategy and a capture lands. */
    public static TriageCompanyStatus parseOrInUniverse(String token) {
        return ApiValueEnum.parse(TriageCompanyStatus.class, token, IN_UNIVERSE, "status");
    }
}
