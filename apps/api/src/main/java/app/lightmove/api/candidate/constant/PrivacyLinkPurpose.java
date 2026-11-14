package app.lightmove.api.candidate.constant;

import app.lightmove.api.common.constant.ApiValueEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

/** What a link to the public privacy page was sent for; stored by name. */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum PrivacyLinkPurpose implements ApiValueEnum {

    NOTICE("notice"),
    /** The stop-emails link one outreach email carries (#924). */
    OPT_OUT("opt_out");

    private final String value;
}
