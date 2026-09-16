package app.lightmove.api.candidate.model;

import app.lightmove.api.candidate.dto.CandidateResponse;

/**
 * What one scoped read tells a contact lookup before it decides whether to spend anything: the
 * profile to ask about, whether each channel was already asked and whether a lookup ever found
 * anything on it, and the row to answer with when nothing needs asking.
 */
public record CandidateContactState(String linkedinUrl, boolean emailsAsked, boolean phonesAsked,
                                    boolean hasFoundEmails, boolean hasFoundPhones, CandidateResponse candidate) {}
