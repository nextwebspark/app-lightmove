package app.lightmove.api.candidate.model;

import app.lightmove.api.candidate.constant.ContactStopKind;
import java.time.Instant;
import java.util.UUID;

/** A standing objection to contact in the consent register (B16); {@code recordedByName} only for the team's. */
public record ObjectionRegisterRow(
        UUID personId,
        String personName,
        ContactStopKind objectedVia,
        Instant objectedAt,
        String recordedByName
) implements ConsentRegisterLine {}
