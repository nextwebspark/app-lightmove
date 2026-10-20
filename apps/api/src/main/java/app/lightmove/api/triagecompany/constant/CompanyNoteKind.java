package app.lightmove.api.triagecompany.constant;

import app.lightmove.api.common.constant.ApiValueEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

/** What a note on a company records, as the composer offers it — the same four a person's notes offer. */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum CompanyNoteKind implements ApiValueEnum {

    GENERAL("general"),
    CALL("call"),
    MEETING("meeting"),
    EMAIL("email");

    private final String value;
}
