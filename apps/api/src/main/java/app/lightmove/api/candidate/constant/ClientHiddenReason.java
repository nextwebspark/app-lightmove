package app.lightmove.api.candidate.constant;

import app.lightmove.api.common.constant.ApiValueEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

/**
 * Why a client seat no longer sees someone whatever staff set (#1488, B12, D2): the firm restricted their processing,
 * or they said no to being shared with this position's client, or withdrew it. Kept by V246's triggers. Staff-only.
 */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum ClientHiddenReason implements ApiValueEnum {

    PROCESSING_RESTRICTED("processing_restricted"),
    SHARE_DECLINED("share_declined"),
    SHARE_WITHDRAWN("share_withdrawn");

    private final String value;
}
