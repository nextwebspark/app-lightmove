package app.lightmove.api.candidate.constant;

import app.lightmove.api.common.constant.ApiValueEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

/** Why a person in a bulk send was not sent a privacy notice. */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum PrivacyNoticeSkipReason implements ApiValueEnum {

    NO_EMAIL("no_email"),
    DO_NOT_CONTACT("do_not_contact"),
    SUPPRESSED("suppressed"),
    PROCESSING_RESTRICTED("processing_restricted"),
    RECENTLY_NOTIFIED("recently_notified"),
    DAILY_CAP("daily_cap");

    private final String value;
}
