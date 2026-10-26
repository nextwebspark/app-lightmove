package app.lightmove.api.candidate.constant;

import app.lightmove.api.common.constant.ApiValueEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

/** A field of the person's record a recorded call may change; the key a timeline line and an audit event name. */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum CallField implements ApiValueEnum {

    NOTICE_PERIOD("noticePeriod"),
    BASE_SALARY("baseSalary"),
    BONUS("bonus"),
    ALLOWANCES("allowances"),
    LOCATION("location"),
    MOTIVATION("motivation"),
    EXPECTED_PACKAGE("expectedPackage"),
    OPEN_TO_RELOCATE("openToRelocate"),
    TASK("task");

    private final String value;
}
