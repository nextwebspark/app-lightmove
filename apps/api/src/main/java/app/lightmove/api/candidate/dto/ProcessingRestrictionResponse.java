package app.lightmove.api.candidate.dto;

import java.time.Instant;
import java.util.UUID;

/** Why processing of a person is restricted, and who restricted it. Staff-only. */
public record ProcessingRestrictionResponse(String reason, UUID restrictedByUserId, String restrictedByName,
                                            Instant restrictedAt) {}
