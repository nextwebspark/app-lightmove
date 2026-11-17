package app.lightmove.api.candidate.dto;

import java.util.List;
import java.util.UUID;

/**
 * One reason for leaving the process. {@code statusIds} are the out-of-process statuses that offer it;
 * {@code requiresNote} asks for a note beside it, as Other does.
 */
public record ExitReasonResponse(
        UUID id,
        String key,
        String label,
        boolean requiresNote,
        int position,
        boolean retired,
        List<UUID> statusIds
) {}
