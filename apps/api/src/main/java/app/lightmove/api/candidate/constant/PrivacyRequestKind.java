package app.lightmove.api.candidate.constant;

import app.lightmove.api.common.constant.ApiValueEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

/**
 * What a person asked for from the public privacy page. An objection is acted on at once; a copy or a deletion waits
 * for an admin.
 */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum PrivacyRequestKind implements ApiValueEnum {

    OBJECTION("objection"),
    COPY("copy"),
    DELETION("deletion");

    private final String value;
}
