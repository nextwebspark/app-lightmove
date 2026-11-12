package app.lightmove.api.pairing.dto;

import app.lightmove.api.candidate.dto.CandidateResponse;
import app.lightmove.api.triagecompany.dto.TriageCompanyResponse;

/**
 * One line of the Companies grid: a person at a company. Exactly one side may be null — {@code candidate} on a company
 * nobody is listed at yet, {@code company} on an executive whose employer is not in the mandate.
 */
public record StageLineResponse(TriageCompanyResponse company, CandidateResponse candidate) {}
