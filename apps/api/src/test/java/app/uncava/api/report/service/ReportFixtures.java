package app.uncava.api.report.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import app.uncava.api.candidate.dto.CandidatePipelineStageDto;
import app.uncava.api.candidate.dto.CandidateResponse;
import app.uncava.api.common.constant.BaseSalaryMode;
import app.uncava.api.core.config.UncavaProperties;
import app.uncava.api.core.config.ReportSettings;
import app.uncava.api.position.dto.CompensationDto;
import app.uncava.api.report.model.ExecutiveRow;
import app.uncava.api.report.model.ReportSources;
import app.uncava.api.triagecompany.dto.TriageCompanyResponse;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** The rows a reporter reads, built with only the fields a chapter looks at. */
final class ReportFixtures {

    static final CandidatePipelineStageDto IDENTIFIED =
            new CandidatePipelineStageDto("identified", "Identified", "neutral", "active", 1);

    private ReportFixtures() {
    }

    static TriageCompanyResponse company(String name, String industry) {
        return new TriageCompanyResponse(UUID.randomUUID(), null, "manual", "inUniverse", null, false, name,
                industry, null, null, null, null, null, null, null, null, null, null, Map.of(), Instant.EPOCH);
    }

    /** An executive mapped at {@code company}, or at no universe company where it is null. */
    static ExecutiveRow executive(String fullName, TriageCompanyResponse company, String seniority, String country,
                                  String nationality, Instant addedAt) {
        CandidateResponse person = new CandidateResponse(UUID.randomUUID(), company == null ? null : company.id(),
                company == null ? "Somewhere Untriaged" : company.companyName(), fullName, null, seniority,
                "identified", null, country, null, nationality, null, null, Set.of(), null, null,
                List.of(), List.of(), List.of(), List.of(), "manual", null, Map.of(), addedAt, null, null, null,
                false, "Identified", "neutral", IDENTIFIED, addedAt, true);
        return new ExecutiveRow(person, company);
    }

    static ReportSources sources(List<TriageCompanyResponse> universe, List<ExecutiveRow> executives) {
        return new ReportSources(null, universe, universe.size(), executives, executives.size(),
                new CompensationDto("USD", null, null, BaseSalaryMode.ANNUAL, null, null, null, null, null, List.of()));
    }

    static UncavaProperties capped(ReportSettings caps) {
        UncavaProperties properties = mock(UncavaProperties.class);
        when(properties.report()).thenReturn(caps);
        return properties;
    }
}
