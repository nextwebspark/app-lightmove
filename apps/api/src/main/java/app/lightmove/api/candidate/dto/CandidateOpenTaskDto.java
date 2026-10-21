package app.lightmove.api.candidate.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * One open task on an executive of a position, as the outreach queue sorts it. {@code rule} is the outreach rule
 * that filed it ({@code reply}, {@code bounce}, {@code booked_call}) or null for one a person set.
 */
public record CandidateOpenTaskDto(
        UUID taskId,
        String title,
        String kind,
        String rule,
        Instant dueAt,
        NextActionAssigneeDto assignee
) {}
