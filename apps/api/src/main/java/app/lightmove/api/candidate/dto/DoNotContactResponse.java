package app.lightmove.api.candidate.dto;

import java.time.Instant;
import java.util.UUID;

/** Why a person must not be approached, and who said so. Staff-only. */
public record DoNotContactResponse(String reason, UUID setByUserId, String setByName, Instant setAt) {}
