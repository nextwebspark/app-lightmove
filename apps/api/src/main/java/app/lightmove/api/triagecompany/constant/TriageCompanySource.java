package app.lightmove.api.triagecompany.constant;

import app.lightmove.api.common.constant.ApiValueEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

/**
 * Which door a company came through. Only {@link #STRATEGY} rows are guaranteed an
 * {@code apolloAccountId} (V34's {@code apollo_source_chk}).
 */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum TriageCompanySource implements ApiValueEnum {

    STRATEGY("strategy"),

    MANUAL("manual"),

    /** Captured off a live page by the browser plugin. */
    EXTENSION("extension"),

    /** Imported from a spreadsheet; kept apart from MANUAL so the badge says nobody checked it by hand. */
    CSV("csv"),

    /** Proposed by the assistant and accepted — a machine chose it, a person agreed. */
    ASSISTANT("assistant");

    private final String value;

    public static TriageCompanySource fromValue(String value) {
        return ApiValueEnum.fromValue(TriageCompanySource.class, value);
    }
}
