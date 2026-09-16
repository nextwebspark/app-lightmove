package app.lightmove.api.candidate.model;

import app.lightmove.api.candidate.dto.CandidateResponse;

/**
 * What one scoped read tells a contact lookup before it decides whether to spend anything: the
 * profile to ask about, what is already held, and the row to answer with when nothing needs asking.
 */
public record CandidateContactState(String linkedinUrl, CandidateContacts contacts,
                                    CandidateResponse candidate) {}
