package app.lightmove.api.triagecompany.dto;

/**
 * {@code skipped} is the rest of what was asked: already held (a declined one is not resurrected),
 * gone from the universe, or off-limits.
 */
public record TriageBulkAddResponse(int added, int skipped) {}
