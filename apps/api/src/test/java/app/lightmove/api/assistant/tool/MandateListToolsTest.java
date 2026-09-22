package app.lightmove.api.assistant.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import app.lightmove.api.candidate.dto.CandidateResponse;
import app.lightmove.api.candidate.dto.CandidatesResponse;
import app.lightmove.api.candidate.service.CandidateService;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.triagecompany.dto.TriageCompaniesResponse;
import app.lightmove.api.triagecompany.dto.TriageCompanyResponse;
import app.lightmove.api.triagecompany.service.TriageCompanyService;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;

/**
 * That a mandate's own capped lists say how much of the mandate they are, and not only what fits.
 *
 * <p>The number these carry is one a consultant quotes to a client. A page read as the whole
 * understates the mandate's own progress, which is a worse answer than a slow one.
 */
class MandateListToolsTest {

    private static final int CAP = 25;
    private static final long MAPPED = 60L;

    @Test
    @DisplayName("the executives answer counts the mandate, not the page")
    void executivesReportWhatTheMandateHolds() {
        CandidateService candidates = mock(CandidateService.class);
        when(candidates.listAllOfProject(any(), any(), anyInt()))
                .thenReturn(new CandidatesResponse(executives(CAP), MAPPED, 0, CAP));

        MandateExecutives answer = new CandidateTools(candidates, cappedAt(CAP))
                .listMandateExecutives(UUID.randomUUID().toString(), callerContext());

        assertThat(answer.matched()).isEqualTo(MAPPED);
        assertThat(answer.showing()).isEqualTo(CAP);
        assertThat(answer.executives()).hasSize(CAP);
    }

    @Test
    @DisplayName("the stage answer counts the stage, not the page")
    void companiesReportWhatTheStageHolds() {
        TriageCompanyService companies = mock(TriageCompanyService.class);
        when(companies.listAllOfStage(any(), any(), any(), any(), anyInt()))
                .thenReturn(new TriageCompaniesResponse(filed(CAP), MAPPED, 0, CAP, null));

        MandateCompanies answer = new TriageCompanyTools(companies, cappedAt(CAP))
                .listMandateCompanies(UUID.randomUUID().toString(), "inUniverse", callerContext());

        assertThat(answer.matched()).isEqualTo(MAPPED);
        assertThat(answer.showing()).isEqualTo(CAP);
        assertThat(answer.companies()).hasSize(CAP);
    }

    private static LightMoveProperties cappedAt(int rows) {
        LightMoveProperties properties = mock(LightMoveProperties.class, RETURNS_DEEP_STUBS);
        when(properties.assistant().toolRowLimit()).thenReturn(rows);
        return properties;
    }

    /** The guard has already run by the time a body executes; here it only has to be present. */
    private static ToolContext callerContext() {
        return new ToolContext(ToolCallerContext.of(
                new AssistantToolCaller(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()),
                text -> {
                }));
    }

    private static List<CandidateResponse> executives(int count) {
        return IntStream.range(0, count)
                .mapToObj(index -> new CandidateResponse(UUID.randomUUID(), null, "Company " + index,
                        "Executive " + index, "CFO", "C_SUITE", "MAPPED", null, null, null, null,
                        null, null, Set.of(), null, null, null, List.of(), List.of(), List.of(),
                        List.of(), "MANUAL", null, null, null, null, null))
                .toList();
    }

    private static List<TriageCompanyResponse> filed(int count) {
        return IntStream.range(0, count)
                .mapToObj(index -> new TriageCompanyResponse(UUID.randomUUID(), "acct-" + index,
                        "APOLLO", "inUniverse", null, false, "Company " + index, "oil & energy",
                        "Saudi Arabia", "Riyadh", 1_000, null, null, null, null, null, null, null,
                        null, null))
                .toList();
    }
}
