package app.lightmove.api.candidate.constant;

import app.lightmove.api.common.constant.ApiValueEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

/**
 * The events that move a candidate forward to a status of the workspace's choosing. {@code seededTarget} is
 * where a workspace that never chose sends them — null when the event moves nobody until it is switched on.
 */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum StatusAutomationTrigger implements ApiValueEnum {

    EMAIL_SENT("emailSent", CandidateSystemStatus.CONTACTED),
    REPLIED("replied", null),
    CALL_BOOKED("callBooked", CandidateSystemStatus.ENGAGED);

    private final String value;
    private final CandidateSystemStatus seededTarget;
}
