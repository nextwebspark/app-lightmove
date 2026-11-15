package app.lightmove.api.candidate.constant;

import app.lightmove.api.common.constant.ApiValueEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

/** How far the firm goes with the people it holds: notices only, or notices and consent requests (#1267). */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum CandidatePrivacyMode implements ApiValueEnum {

    NOTICES("notices"),
    CONSENT("consent");

    private final String value;
}
