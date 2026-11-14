package app.lightmove.api.outreach.dto;

import app.lightmove.api.outreach.constant.OutreachSkipReason;
import java.util.List;
import java.util.UUID;

/**
 * One executive in the Choose step. {@code skipReason} null means they can be added; otherwise they are
 * shown dimmed with the reason, and {@code inSequence} names the sequence that already holds them.
 * {@code privacyFooter} is the sentence the privacy footer opens with for them, null when data protection is off.
 */
public record EnrollmentCandidateResponse(UUID candidateId, UUID personId, UUID triageCompanyId, String fullName,
                                          String title, String companyName, List<RecipientEmailResponse> emails,
                                          OutreachSkipReason skipReason, String inSequence,
                                          SequenceTokensResponse tokens, String privacyFooter) {}
