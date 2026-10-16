package app.uncava.api.candidate.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * What happens next with an executive on one position: the earliest open task, who holds it, and how many are
 * open in all. {@code dueAt} is null only on a task V133 carried over from a next action set without a date.
 */
public record CandidateNextActionDto(
        UUID taskId,
        String text,
        String kind,
        Instant dueAt,
        NextActionAssigneeDto assignee,
        int openCount,
        String setByName
) {}
