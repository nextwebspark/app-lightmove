package app.lightmove.api.triagecompany.constant;

import app.lightmove.api.common.constant.ApiValueEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

/** Where a company stands in a mandate's triage; untriaged means no row at all. */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum TriageCompanyStatus implements ApiValueEnum {

    IN_UNIVERSE("inUniverse"),

    SHORTLISTED("shortlisted"),

    /** Kept rather than deleted, so a later "Add all to Universe" cannot resurrect it. */
    DECLINED("declined");

    private final String value;

    public static TriageCompanyStatus fromValue(String value) {
        return ApiValueEnum.fromValue(TriageCompanyStatus.class, value);
    }

    public static TriageCompanyStatus parseOrInUniverse(String token) {
        return ApiValueEnum.parse(TriageCompanyStatus.class, token, IN_UNIVERSE, "status");
    }
}
