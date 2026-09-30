package app.lightmove.api.candidate.model;

import app.lightmove.api.triagecompany.constant.TriageCompanySource;
import app.lightmove.api.triagecompany.constant.TriageCompanyStatus;
import app.lightmove.api.triagecompany.model.CapturedCompanyDetails;

/** The employer a search hit names, filed into the mandate at {@code stage} in the same write as the person. */
public record ResearchedEmployer(CapturedCompanyDetails details, TriageCompanySource source,
                                 TriageCompanyStatus stage) {}
