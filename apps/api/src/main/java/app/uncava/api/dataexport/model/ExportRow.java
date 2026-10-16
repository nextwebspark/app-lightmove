package app.uncava.api.dataexport.model;

import app.uncava.api.candidate.dto.CandidateResponse;
import app.uncava.api.triagecompany.dto.TriageCompanyResponse;

/** One file line, as one grid line: a person at a company. Either side may be absent, never both. */
public record ExportRow(TriageCompanyResponse company, CandidateResponse candidate) {}
