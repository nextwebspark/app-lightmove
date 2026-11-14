package app.lightmove.api.outreach.dto;

import app.lightmove.api.candidate.dto.CandidateStatusTargetDto;

/**
 * What the composer says before anything is sent, for the caller. {@code replySubject} is null where no thread with
 * this person on this position is still writable from their mailbox; {@code emailSentMovesTo} null where they'd stay;
 * the {@code tokens}' booking link only where one exists, read and never claimed. {@code privacyFooter} is the sentence
 * the privacy footer opens with (#924), null when data protection is off.
 */
public record ConversationComposerResponse(String replySubject, boolean replyStopsRun,
                                           CandidateStatusTargetDto emailSentMovesTo,
                                           AttachmentLimitsResponse attachmentLimits,
                                           SequenceTokensResponse tokens, String privacyFooter) {}
