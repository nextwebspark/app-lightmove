package app.lightmove.api.candidate.model;

import app.lightmove.api.candidate.constant.ConsentAnsweredVia;
import app.lightmove.api.candidate.constant.ConsentPurpose;
import app.lightmove.api.candidate.constant.ConsentStatus;
import app.lightmove.api.candidate.constant.ContactStopKind;
import java.time.Instant;
import java.util.UUID;

/**
 * One line of the consent register (#1267): a person's standing on one purpose, and the words they were asked in; or
 * their objection to being contacted (B16), when {@code objectedVia} names the door and purpose and status are null.
 */
public record ConsentRegisterRow(
        UUID personId,
        String personName,
        ConsentPurpose purpose,
        String positionTitle,
        boolean clientNamed,
        ConsentStatus status,
        Instant requestedAt,
        String requestedByName,
        Instant answeredAt,
        ConsentAnsweredVia answeredVia,
        Instant validUntil,
        String wordingVersion,
        String wording,
        ContactStopKind objectedVia
) {

    /** {@code recordedByName} is the colleague who recorded it, null when the person used their own link. */
    public static ConsentRegisterRow objection(UUID personId, String personName, ContactStopKind objectedVia,
                                               Instant objectedAt, String recordedByName) {
        return new ConsentRegisterRow(personId, personName, null, null, false, null, null, recordedByName,
                objectedAt, null, null, null, null, objectedVia);
    }

    public boolean isObjection() {
        return objectedVia != null;
    }
}
