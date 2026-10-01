package app.lightmove.api.candidate.constant;

import app.lightmove.api.common.constant.ApiValueEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

/** What a note records, as the composer offers it. */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum PersonNoteKind implements ApiValueEnum {

    GENERAL("general"),
    CALL("call"),
    MEETING("meeting"),
    EMAIL("email");

    private final String value;

    public static PersonNoteKind fromValue(String value) {
        return ApiValueEnum.fromValue(PersonNoteKind.class, value);
    }
}
