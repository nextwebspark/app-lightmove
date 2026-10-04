package app.lightmove.api.candidate.constant;

import app.lightmove.api.common.constant.ApiValueEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

/** What an upload became: a document of its own, or the next version of one already on the person. */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum PersonDocumentUploadOutcome implements ApiValueEnum {

    CREATED("created"),
    NEW_VERSION("new_version");

    private final String value;
}
