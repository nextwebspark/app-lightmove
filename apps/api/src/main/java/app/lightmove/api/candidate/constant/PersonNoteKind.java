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

    GENERAL("general", "General"),
    CALL("call", "Call"),
    MEETING("meeting", "Meeting"),
    EMAIL("email", "Email"),
    LINKEDIN("linkedin", "LinkedIn");

    private final String value;
    /** How a note's kind is named to the model. */
    private final String promptLabel;

    public static PersonNoteKind fromValue(String value) {
        return ApiValueEnum.fromValue(PersonNoteKind.class, value);
    }
}
