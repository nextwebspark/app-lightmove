package app.lightmove.api.candidate.dto;

import java.util.UUID;

/** One person a bulk consent request left out; {@code reason} is a {@code ConsentRequestSkipReason} wire token. */
public record ConsentRequestSkipResponse(UUID personId, String reason) {}
