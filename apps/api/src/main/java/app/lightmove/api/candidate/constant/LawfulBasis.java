package app.lightmove.api.candidate.constant;

import app.lightmove.api.common.constant.ApiValueEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

/**
 * Why the firm may hold a person's data (GDPR Art. 6). Stored by name; a person with none follows the workspace's
 * default.
 */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum LawfulBasis implements ApiValueEnum {

    LEGITIMATE_INTEREST("legitimate_interest"),
    CONSENT("consent"),
    CONTRACT("contract");

    private final String value;
}
