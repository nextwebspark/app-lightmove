package app.lightmove.api.candidate.constant;

import app.lightmove.api.common.constant.ApiValueEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

/** The key a suppression is held on: the two that identify a human, never a phone or a name. */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum SuppressionKind implements ApiValueEnum {

    PROFILE_SLUG("linkedin"),
    EMAIL("email");

    private final String value;
}
