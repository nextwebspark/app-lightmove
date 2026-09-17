package app.lightmove.api.report.model;

import app.lightmove.api.candidate.constant.CandidateStatus;
import app.lightmove.api.candidate.constant.Gender;
import app.lightmove.api.candidate.dto.CandidateResponse;
import app.lightmove.api.common.constant.Seniority;
import app.lightmove.api.triagecompany.dto.TriageCompanyResponse;
import java.time.Instant;
import java.util.Optional;

/**
 * One mapped executive paired with the universe company they sit at, where they sit at one. The
 * pairing is the report's own — {@code triagecompany} never learns that people exist — and it is
 * what gives an executive a sector.
 */
public record ExecutiveRow(CandidateResponse executive, TriageCompanyResponse company) {

    public boolean hasCompany() {
        return company != null;
    }

    public Optional<String> sector() {
        return Optional.ofNullable(company).map(TriageCompanyResponse::industry);
    }

    /** Null while the row carries no seniority, which the matrix reports rather than guesses. */
    public Seniority seniority() {
        return Seniority.fromValue(executive.seniority());
    }

    public CandidateStatus status() {
        return CandidateStatus.fromValue(executive.status());
    }

    /** Null while nobody recorded one, which the chapter reports rather than guesses. */
    public Gender gender() {
        return Gender.fromValue(executive.gender());
    }

    public Instant mappedAt() {
        return executive.addedAt();
    }

    public String employerName() {
        return company != null ? company.companyName() : executive.companyName();
    }
}
