package app.lightmove.api.outreach.dto;

import app.lightmove.api.candidate.dto.CandidateStatusTargetDto;

/**
 * What the composer says before anything is sent, for the caller.
 *
 * @param replySubject     the subject a reply from the caller's mailbox would carry; null where they have no thread
 *                         with this person on this position their mailbox can still write into
 * @param emailSentMovesTo the status an email now would move the person to; null where they would stay
 */
public record ConversationComposerResponse(String replySubject, CandidateStatusTargetDto emailSentMovesTo) {}
