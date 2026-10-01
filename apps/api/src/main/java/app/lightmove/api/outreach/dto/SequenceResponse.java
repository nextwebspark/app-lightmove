package app.lightmove.api.outreach.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** A sequence card and the editor's read. {@code enrolledCount} counts everyone ever put on it. */
public record SequenceResponse(UUID id, String name, List<SequenceStepResponse> steps, String createdByName,
                               long enrolledCount, Instant updatedAt) {}
