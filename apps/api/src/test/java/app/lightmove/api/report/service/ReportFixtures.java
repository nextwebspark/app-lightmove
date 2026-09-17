package app.lightmove.api.report.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import app.lightmove.api.candidate.dto.CandidateResponse;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.config.ReportSettings;
import app.lightmove.api.position.constant.BaseSalaryMode;
import app.lightmove.api.position.dto.CompensationDto;
import app.lightmove.api.report.model.ExecutiveRow;
import app.lightmove.api.report.model.ReportSources;
import app.lightmove.api.triagecompany.dto.TriageCompanyResponse;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** The rows a reporter reads, built with only the fields a chapter looks at. */
final class ReportFixtures {

    private ReportFixtures() {
    }

    static TriageCompanyResponse company(String name, String industry) {
        return new TriageCompanyResponse(UUID.randomUUID(), null, "manual", "inUniverse", null, name, industry,
                null, null, null, null, null, null, null, null, null, null, Map.of(), Instant.EPOCH);
    }

    /** An executive mapped at {@code company}, or at no universe company where it is null. */
    static ExecutiveRow executive(String fullName, TriageCompanyResponse company, String seniority, String country,
                                  String nationality, Instant addedAt) {
        CandidateResponse person = new CandidateResponse(UUID.randomUUID(), company == null ? null : company.id(),
                company == null ? "Somewhere Untriaged" : company.companyName(), fullName, null, seniority,
                "identified", null, country, null, nationality, null, null, null, null, null, List.of(), List.of(),
                List.of(), List.of(), "manual", null, Map.of(), addedAt, null, null);
        return new ExecutiveRow(person, company);
    }

    static ReportSources sources(List<TriageCompanyResponse> universe, List<ExecutiveRow> executives) {
        return new ReportSources(null, universe, universe.size(), executives, executives.size(),
                new CompensationDto("USD", null, null, BaseSalaryMode.ANNUAL, null, null, null, null, null, List.of()));
    }

    static LightMoveProperties capped(ReportSettings caps) {
        LightMoveProperties properties = mock(LightMoveProperties.class);
        when(properties.report()).thenReturn(caps);
        return properties;
    }
}
