package app.lightmove.api.candidate.constant;

import app.lightmove.api.common.constant.ApiValueEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

/**
 * How a person was last told the firm holds their data (Art. 14): a privacy email, the footer of an outreach email,
 * or a colleague marking it by hand.
 */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum PrivacyNoticeChannel implements ApiValueEnum {

    PRIVACY_EMAIL("privacy_email"),
    OUTREACH_EMAIL("outreach_email"),
    MARKED_BY_HAND("marked_by_hand");

    private final String value;
}
