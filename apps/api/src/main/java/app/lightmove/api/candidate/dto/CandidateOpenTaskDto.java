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
) {

    /** {@code rule} of a task the reply rule filed; {@code task} holds these to its own rule names. */
    public static final String REPLY_RULE = "reply";

    /** {@code rule} of a task the bounce rule filed. */
    public static final String BOUNCE_RULE = "bounce";
}
