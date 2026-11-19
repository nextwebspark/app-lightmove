package app.lightmove.api.candidate.constant;

import app.lightmove.api.core.constant.ApiValueEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

/** A yes/no column filter on the Candidates page: a LinkedIn profile on file, do not contact. */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum PoolPresence implements ApiValueEnum {

    YES("yes"),
    NO("no");

    private final String value;
}
