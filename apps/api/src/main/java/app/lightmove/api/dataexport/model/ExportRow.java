package app.lightmove.api.dataexport.model;

import app.lightmove.api.candidate.dto.CandidateResponse;
import app.lightmove.api.triagecompany.dto.TriageCompanyResponse;

/**
 * One line of the exported file, which is one line of the grid: a person at a company.
 *
 * <p>Either side may be absent, never both — a company nobody has been mapped at is a row with no
 * executive, and an executive whose employer is not in the mandate's universe is a row with no
 * company.
 */
public record ExportRow(TriageCompanyResponse company, CandidateResponse candidate) {}
