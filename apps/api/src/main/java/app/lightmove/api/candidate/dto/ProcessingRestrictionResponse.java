package app.lightmove.api.candidate.dto;

import java.time.Instant;
import java.util.UUID;

/** Why processing of a person is restricted, who restricted it, and whether the caller may lift it. Staff-only. */
public record ProcessingRestrictionResponse(String reason, UUID restrictedByUserId, String restrictedByName,
                                            Instant restrictedAt, boolean mayLift) {}
