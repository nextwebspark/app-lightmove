package app.lightmove.api.candidate.constant;

import app.lightmove.api.core.constant.ApiValueEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

/** What a person is asked to agree to (#1267); a share is one position's, the others are the person's. */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum ConsentPurpose implements ApiValueEnum {

    KEEP_ON_FILE("keep_on_file"),
    SHARE_WITH_CLIENT("share_with_client"),
    MARKETING("marketing");

    private final String value;
}
