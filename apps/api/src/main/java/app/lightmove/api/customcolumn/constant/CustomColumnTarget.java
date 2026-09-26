package app.lightmove.api.customcolumn.constant;

import app.lightmove.api.common.constant.ApiValueEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

/**
 * Which half of a Companies-grid row a custom column describes.
 *
 * <p>A row on that screen is a <i>person at a company</i>, so an extra column is always a fact about
 * one or the other and never about both: "Founded" belongs to the company, "Ethnicity" to the person.
 * Without this distinction an import would have to guess which record an unmapped column lands on, and
 * a company with three executives would either repeat a company fact three times or scatter a personal
 * one across the wrong rows.
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
