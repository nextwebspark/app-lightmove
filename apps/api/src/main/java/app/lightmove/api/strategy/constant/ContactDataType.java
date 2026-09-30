package app.lightmove.api.strategy.constant;

import app.lightmove.api.common.constant.ApiValueEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

/**
 * A channel a people search can require ContactOut to hold — {@code data_types}. A filter, never a
 * reveal: nothing is bought for it while {@code reveal_info} stays false.
 */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum ContactDataType implements ApiValueEnum {

    PERSONAL_EMAIL("personal_email"),
    WORK_EMAIL("work_email"),
    PHONE("phone");

    private final String value;

    public static ContactDataType fromValue(String value) {
        return ApiValueEnum.fromValue(ContactDataType.class, value);
    }
}
