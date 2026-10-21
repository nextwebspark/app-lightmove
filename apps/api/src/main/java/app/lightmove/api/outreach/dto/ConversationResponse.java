package app.lightmove.api.outreach.dto;

import app.lightmove.api.candidate.dto.CandidateOpenTaskDto;
import app.lightmove.api.candidate.dto.PipelineRowResponse;
import java.util.List;

/**
 * One executive's conversation on a position: who they are and where they stand, their open tasks, their
 * latest run, the key dates, how they are reached, what an email from the caller would do, and every email, run
 * event, note and meeting newest first. Staff-only.
 */
public record ConversationResponse(
        PipelineRowResponse executive,
        List<CandidateOpenTaskDto> openTasks,
        CandidateOutreachResponse currentRun,
        ConversationKeyDatesResponse keyDates,
        ConversationContactResponse contact,
        ConversationComposerResponse composer,
        List<ConversationEventResponse> events,
        /** The history held more than one read returns; the oldest are left off. */
        boolean truncated
) {}
