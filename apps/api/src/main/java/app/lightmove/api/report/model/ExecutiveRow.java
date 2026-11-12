package app.lightmove.api.report.model;

import app.lightmove.api.candidate.constant.Gender;
import app.lightmove.api.candidate.dto.CandidatePipelineStageDto;
import app.lightmove.api.candidate.dto.CandidateResponse;
import app.lightmove.api.common.constant.Seniority;
import app.lightmove.api.triagecompany.dto.TriageCompanyResponse;
import java.time.Instant;
import java.util.Optional;

/**
 * An executive paired with their universe company, where there is one — the pairing that gives an
 * executive a sector, done here so {@code triagecompany} never learns people exist.
 */
public record ExecutiveRow(CandidateResponse executive, TriageCompanyResponse company, int interestedFrom) {

    /** A row read where no stage counts as interested: tests and reads that never ask. */
    public ExecutiveRow(CandidateResponse executive, TriageCompanyResponse company) {
        this(executive, company, Integer.MAX_VALUE);
    }

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

    public CandidatePipelineStageDto pipelineStage() {
        return executive.pipelineStage();
    }

    /**
     * Interested, or further on and still in process: a stage at or after the one the built-in Interested status
     * sits under ({@code interestedFrom}, its position).
     */
    public boolean isInterestedOrLater() {
        CandidatePipelineStageDto stage = executive.pipelineStage();
        return stage != null && !executive.statusOutOfProcess() && stage.position() >= interestedFrom;
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
