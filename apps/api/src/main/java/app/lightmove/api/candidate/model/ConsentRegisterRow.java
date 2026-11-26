package app.lightmove.api.candidate.model;

import app.lightmove.api.candidate.constant.ConsentAnsweredVia;
import app.lightmove.api.candidate.constant.ConsentPurpose;
import app.lightmove.api.candidate.constant.ConsentStatus;
import java.time.Instant;
import java.util.UUID;

/** One line of the consent register (#1267): a person's standing on one purpose, and the words they were asked in. */
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
        String wording
) implements ConsentRegisterLine {}
