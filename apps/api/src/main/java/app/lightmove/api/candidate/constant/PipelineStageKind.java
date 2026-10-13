package app.lightmove.api.candidate.constant;

import app.lightmove.api.common.constant.ApiValueEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

/**
 * Whether a stage keeps someone in the running. A workspace has exactly one {@link #CLOSED} stage, last:
 * nobody at one of its statuses is approached again on that position.
 */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum PipelineStageKind implements ApiValueEnum {

    ACTIVE("active"),
    CLOSED("closed");

    private final String value;
}
