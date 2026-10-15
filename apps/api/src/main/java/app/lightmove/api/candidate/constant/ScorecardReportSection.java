package app.lightmove.api.candidate.constant;

import app.lightmove.api.common.constant.ApiValueEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

/** The five sections of an assessment's written report, spelled as the API and the SPA spell them. */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum ScorecardReportSection implements ApiValueEnum {

    CAREER_SUMMARY("careerSummary"),
    CORE_VALUE("coreValue"),
    CONCERNS("concerns"),
    WORKING_STYLE("workingStyle"),
    MOTIVATION("motivation");

    private final String value;
}
