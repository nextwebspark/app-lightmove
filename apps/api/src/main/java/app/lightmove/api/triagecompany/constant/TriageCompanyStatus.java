package app.lightmove.api.triagecompany.constant;

import app.lightmove.api.common.constant.ApiValueEnum;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

/** Where a company stands in a mandate's triage; untriaged means no row at all. */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum TriageCompanyStatus implements ApiValueEnum {

    IN_UNIVERSE("inUniverse"),

    /** Retired as a destination: rows already here keep it and every read answers it, but nothing new is filed here. */
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

    /** A stage a write files or moves a company or an executive to; in universe when blank. */
    public static TriageCompanyStatus parseDestination(String token) {
        return requireDestination(parseOrInUniverse(token));
    }

    /** {@code stage} itself, or a 400 when it is {@link #SHORTLISTED}, which nothing new may be filed at. */
    public static TriageCompanyStatus requireDestination(TriageCompanyStatus stage) {
        if (stage == SHORTLISTED) {
            throw ApiException.withField(ErrorCode.VALIDATION_FAILED, "status",
                    "Shortlisted is no longer a stage. File at In universe or Declined.");
        }
        return stage;
    }
}
