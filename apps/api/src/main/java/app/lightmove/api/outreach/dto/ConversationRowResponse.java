package app.lightmove.api.outreach.dto;

import app.lightmove.api.candidate.dto.CandidateOpenTaskDto;
import app.lightmove.api.outreach.constant.ConversationGroup;
import java.time.Instant;
import java.util.UUID;

/**
 * One executive in the Outreach page's queue. {@code group} is null outside Needs action. {@code action} is the task
 * to act on — in Attention the bounce task, else their soonest open one — and null only when nothing is open.
 * Staff-only.
 */
public record ConversationRowResponse(
        UUID candidateId,
        UUID personId,
        String fullName,
        String title,
        String companyName,
        /** When research filled the profile in; the photo is asked for only once it has. */
        String enrichedAt,
        String status,
        String statusLabel,
        String statusColour,
        ConversationGroup group,
        CandidateOpenTaskDto action,
        /** Their latest run on the position, or null. */
        OutreachRunResponse run,
        ConversationMessageSummaryResponse latest,
        Instant lastSentAt,
        /** When their next email goes, or null. */
        Instant scheduledAt,
        Instant lastActivityAt
) {}
