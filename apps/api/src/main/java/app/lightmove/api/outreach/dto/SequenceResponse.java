package app.lightmove.api.outreach.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A sequence card and the editor's read. {@code enrolledCount} counts everyone ever put on it,
 * {@code sentCount} the emails it has sent, {@code repliedCount} those who answered.
 */
public record SequenceResponse(UUID id, String name, List<SequenceStepResponse> steps,
                               SequenceScheduleResponse schedule, String createdByName,
                               long enrolledCount, long sentCount, long repliedCount, Instant updatedAt) {}
