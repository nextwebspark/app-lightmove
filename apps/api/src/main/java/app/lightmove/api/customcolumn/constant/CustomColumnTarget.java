package app.lightmove.api.customcolumn.constant;

import app.lightmove.api.common.constant.ApiValueEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

/**
 * Which half of a row — the person or the company — a custom column describes; never both, or a
 * company fact would repeat per executive.
 */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum CustomColumnTarget implements ApiValueEnum {

    /** A fact about the company — stored on the mandate's triage row. */
    COMPANY("company"),

    /** A fact about the person — stored on the mandate's candidate row. */
    CANDIDATE("candidate");

    private final String value;

    public static CustomColumnTarget fromValue(String value) {
        return ApiValueEnum.fromValue(CustomColumnTarget.class, value);
    }
}
