package app.lightmove.api.candidate.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Another position mapping the person, and where they stand on it. {@code workable} says whether the caller may open
 * it; the title is shown either way, as the drawer's Positions shows it.
 */
public record ApproachPositionResponse(UUID projectId, String positionTitle, String statusLabel, String statusColour,
                                       boolean inProcess, boolean placed, String addedByName, Instant statusChangedAt,
                                       boolean workable) {}
