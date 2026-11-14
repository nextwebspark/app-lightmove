package app.lightmove.api.candidate.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Another position mapping the person, and where they stand on it. On one the caller cannot open, {@code visible} is
 * false and only the stage and whether they are in process are said: no id, title, status or who added them.
 */
public record ApproachPositionResponse(UUID projectId, String positionTitle, String statusLabel, String statusColour,
                                       String stageLabel, boolean inProcess, boolean placed, String addedByName,
                                       Instant statusChangedAt, boolean visible) {}
