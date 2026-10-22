package app.lightmove.api.candidate.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import app.lightmove.api.candidate.constant.CandidateSource;
import app.lightmove.api.candidate.constant.EnrichmentVendor;
import app.lightmove.api.candidate.model.Candidate;
import app.lightmove.api.candidate.model.CandidateCareerEntry;
import app.lightmove.api.candidate.model.CandidateCompensation;
import app.lightmove.api.candidate.model.CandidateDetails;
import app.lightmove.api.candidate.model.CandidateProfile;
import app.lightmove.api.candidate.model.EnrichedProfile;
import app.lightmove.api.candidate.model.Person;
import app.lightmove.api.candidate.model.PipelineTestStatuses;
import app.lightmove.api.candidate.model.PersonEmployer;
import app.lightmove.api.triagecompany.dto.TriageCompanyResponse;
import app.lightmove.api.triagecompany.model.CapturedCompanyDetails;
import app.lightmove.api.triagecompany.service.TriageCompanyReadService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Research's employer logo is drawn only beside the employer research named, and a further position files
 * someone under an employer a position or research named, never one guessed from their career.
 */
class PersonEmployerTest {

    private static final String RESEARCHED_LOGO = "https://media.example.com/alrawabi.png";

    private static final EnrichedProfile RESEARCH = new EnrichedProfile(
            "Group CFO", null, "Al Rawabi Dairy", "https://www.linkedin.com/company/alrawabi/", RESEARCHED_LOGO,
            null, null, List.of(new CandidateCareerEntry("Al Rawabi Dairy", "Group CFO", "2021 – Present", null)),
            List.of(), List.of(), List.of(), null, EnrichmentVendor.BRIGHTDATA);

    private static final String COMPANY_PAGE = "https://www.linkedin.com/company/alrawabi/";

    private static final String COMPANY_LOGO = "https://media.example.com/alrawabi-row.png";

    private static final UUID WORKSPACE = UUID.randomUUID();

    private final TriageCompanyReadService companies = mock(TriageCompanyReadService.class);

    private final PersonEmployerResolver resolver = new PersonEmployerResolver(companies);

    @Test
    @DisplayName("a position recording the researched employer carries its company row and research's logo")
    void aPositionAtTheResearchedEmployer() {
        Person person = researched();
        UUID companyId = UUID.randomUUID();

        PersonEmployer employer = PersonEmployerResolver.employerOf(person,
                List.of(mappedAt(person, companyId, "al rawabi dairy")));

        assertThat(employer).isEqualTo(new PersonEmployer("al rawabi dairy", companyId, RESEARCHED_LOGO));
    }

    @Test
    @DisplayName("a position recording another employer never borrows research's logo")
    void aPositionAtAnotherEmployer() {
        Person person = researched();

        PersonEmployer employer = PersonEmployerResolver.employerOf(person,
                List.of(mappedAt(person, null, "Emirates NBD")));

        assertThat(employer).isEqualTo(new PersonEmployer("Emirates NBD", null, null));
    }

    @Test
    @DisplayName("someone on no position is named by their career, with research's logo")
    void noPositionFallsBackToTheCareer() {
        assertThat(PersonEmployerResolver.employerOf(researched(), List.of()))
                .isEqualTo(new PersonEmployer("Al Rawabi Dairy", null, RESEARCHED_LOGO));
    }

    @Test
    @DisplayName("research naming no employer keeps the last researched logo rather than one that can never match")
    void anEmployerlessResearchKeepsTheLastMark() {
        Person person = researched();

        person.enrich(new EnrichedProfile("Group CFO", null, null, "https://www.linkedin.com/company/other/",
                "https://media.example.com/other.png", null, null, List.of(), List.of(), List.of(), List.of(), null,
                EnrichmentVendor.BRIGHTDATA));

        assertThat(PersonEmployerResolver.employerOf(person, List.of()).researchedLogoUrl()).isEqualTo(RESEARCHED_LOGO);
    }

    @Test
    @DisplayName("a further position files someone under the employer their last position named, research's aside")
    void aFurtherPositionFilesUnderTheNamedEmployer() {
        Person person = researched();
        List<Candidate> positions = List.of(mappedAt(person, null, "Emirates NBD"));

        assertThat(resolver.knownEmployerOf(UUID.randomUUID(), person, positions))
                .contains(CapturedCompanyDetails.employerNamed("Emirates NBD", null, null));
    }

    @Test
    @DisplayName("a further position files someone under their last position's company row, LinkedIn page and all")
    void aFurtherPositionFilesUnderTheCompanyRow() {
        Person person = researched();
        UUID companyId = UUID.randomUUID();
        Candidate position = mappedAt(person, companyId, "Al Rawabi Dairy");
        when(companies.get(WORKSPACE, position.getProjectId(), companyId)).thenReturn(companyRow(companyId));

        assertThat(resolver.knownEmployerOf(WORKSPACE, person, List.of(position)))
                .contains(CapturedCompanyDetails.employerNamed("Al Rawabi Dairy", COMPANY_PAGE, COMPANY_LOGO));
    }

    @Test
    @DisplayName("a position that recorded the name alone borrows the row an older position at that employer holds")
    void aNameOnlyPositionBorrowsAnOlderRow() {
        Person person = researched();
        UUID companyId = UUID.randomUUID();
        Candidate older = mappedAt(person, companyId, "Al Rawabi Dairy");
        Candidate newer = mappedAt(person, null, "al rawabi dairy");
        when(companies.get(WORKSPACE, older.getProjectId(), companyId)).thenReturn(companyRow(companyId));

        assertThat(resolver.knownEmployerOf(WORKSPACE, person, List.of(older, newer)))
                .contains(CapturedCompanyDetails.employerNamed("Al Rawabi Dairy", COMPANY_PAGE, COMPANY_LOGO));
    }

    @Test
    @DisplayName("on no position, a further position files someone under research's employer")
    void aFurtherPositionFallsBackToResearch() {
        assertThat(resolver.knownEmployerOf(UUID.randomUUID(), researched(), List.of()))
                .contains(CapturedCompanyDetails.employerNamed("Al Rawabi Dairy", null, RESEARCHED_LOGO));
    }

    @Test
    @DisplayName("a further position never files someone under their career's first post, which may be one they left")
    void aFurtherPositionNeverFilesUnderTheCareer() {
        Person person = Person.founded(UUID.randomUUID(), UUID.randomUUID(), CandidateSource.MANUAL,
                details(null, List.of(new CandidateCareerEntry("Somewhere", "Advisor", "2020 –", null))));

        assertThat(PersonEmployerResolver.employerOf(person, List.of()).name()).isEqualTo("Somewhere");
        assertThat(resolver.knownEmployerOf(UUID.randomUUID(), person, List.of())).isEmpty();
    }

    private static Person researched() {
        Person person = Person.founded(UUID.randomUUID(), UUID.randomUUID(), CandidateSource.EXTENSION,
                details(null));
        person.enrich(RESEARCH);
        return person;
    }

    private static Candidate mappedAt(Person person, UUID triageCompanyId, String companyName) {
        return Candidate.mapped(UUID.randomUUID(), UUID.randomUUID(), triageCompanyId, person,
                CandidateSource.MANUAL, PipelineTestStatuses.identified(), details(companyName));
    }

    private static TriageCompanyResponse companyRow(UUID id) {
        return new TriageCompanyResponse(id, "a42", "extension", "inUniverse", null, false, "Al Rawabi Dairy",
                null, null, null, null, null, null, COMPANY_PAGE, null, null, null, COMPANY_LOGO, Map.of(),
                Instant.now(), null, null, null);
    }

    private static CandidateDetails details(String employerName) {
        return details(employerName, null);
    }

    private static CandidateDetails details(String employerName, List<CandidateCareerEntry> career) {
        return new CandidateDetails("Sample Person", null, null, employerName,
                null, null, "https://www.linkedin.com/in/sample-profile", null, null, null, null,
                null, null, null, CandidateCompensation.unknown(),
                new CandidateProfile(career, null, null, null, null, null), null);
    }
}
