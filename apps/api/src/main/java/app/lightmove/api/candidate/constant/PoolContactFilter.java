package app.lightmove.api.candidate.constant;

import app.lightmove.api.common.constant.ApiValueEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

/** Which channels the contact ledger holds for a person, as the Contact column filters them. */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum PoolContactFilter implements ApiValueEnum {

    EMAIL("email"),
    PHONE("phone"),

    /** Neither an email nor a phone on file. */
    NONE("none");

    private final String value;
}
