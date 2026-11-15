package app.lightmove.api.candidate.constant;

import app.lightmove.api.common.constant.ApiValueEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

/** Why a person in a bulk consent request was not asked. */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum ConsentRequestSkipReason implements ApiValueEnum {

    NO_EMAIL("no_email"),
    DO_NOT_CONTACT("do_not_contact"),
    SUPPRESSED("suppressed"),
    PROCESSING_RESTRICTED("processing_restricted"),
    ALREADY_GIVEN("already_given"),
    RECENTLY_ASKED("recently_asked"),
    DAILY_CAP("daily_cap");

    private final String value;
}
