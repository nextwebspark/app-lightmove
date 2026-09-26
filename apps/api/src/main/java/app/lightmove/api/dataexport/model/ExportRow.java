package app.lightmove.api.dataexport.model;

import app.lightmove.api.candidate.dto.CandidateResponse;
import app.lightmove.api.triagecompany.dto.TriageCompanyResponse;

/** One file line, as one grid line: a person at a company. Either side may be absent, never both. */
public record ExportRow(TriageCompanyResponse company, CandidateResponse candidate) {}
