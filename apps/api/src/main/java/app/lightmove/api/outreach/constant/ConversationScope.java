package app.lightmove.api.outreach.constant;

import app.lightmove.api.common.constant.ApiValueEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

/** Mine or Team: whether the queue holds only the caller's work or everyone's on the position. */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum ConversationScope implements ApiValueEnum {
    MINE("mine"),
    TEAM("team");

    private final String value;
}
