package app.lightmove.api.candidate.service;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.candidate.constant.CandidateSource;
import app.lightmove.api.candidate.constant.CandidateStatus;
import app.lightmove.api.candidate.constant.EnrichmentVendor;
import app.lightmove.api.candidate.model.Candidate;
import app.lightmove.api.candidate.model.CandidateCareerEntry;
import app.lightmove.api.candidate.model.CandidateCompensation;
import app.lightmove.api.candidate.model.CandidateDetails;
import app.lightmove.api.candidate.model.CandidateProfile;
import app.lightmove.api.candidate.model.EnrichedProfile;
import app.lightmove.api.candidate.model.Person;
import app.lightmove.api.candidate.model.PersonEmployer;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Research's employer logo is drawn only beside the employer research named. */
class PersonEmployerTest {

    private static final String RESEARCHED_LOGO = "https://media.example.com/alrawabi.png";

    private static final EnrichedProfile RESEARCH = new EnrichedProfile(
            "Group CFO", null, "Al Rawabi Dairy", "https://www.linkedin.com/company/alrawabi/", RESEARCHED_LOGO,
            null, null, List.of(new CandidateCareerEntry("Al Rawabi Dairy", "Group CFO", "2021 – Present", null)),
            List.of(), List.of(), List.of(), null, EnrichmentVendor.BRIGHTDATA);

    @Test
    @DisplayName("a position recording the researched employer carries its company row and research's logo")
    void aPositionAtTheResearchedEmployer() {
        Person person = researched();
        UUID companyId = UUID.randomUUID();

        PersonEmployer employer = PersonRecordService.employerOf(person,
                List.of(mappedAt(person, companyId, "al rawabi dairy")));

        assertThat(employer).isEqualTo(new PersonEmployer("al rawabi dairy", companyId, RESEARCHED_LOGO));
    }

    @Test
    @DisplayName("a position recording another employer never borrows research's logo")
    void aPositionAtAnotherEmployer() {
        Person person = researched();

        PersonEmployer employer = PersonRecordService.employerOf(person,
                List.of(mappedAt(person, null, "Emirates NBD")));

        assertThat(employer).isEqualTo(new PersonEmployer("Emirates NBD", null, null));
    }

    @Test
    @DisplayName("someone on no position is named by their career, with research's logo")
    void noPositionFallsBackToTheCareer() {
        assertThat(PersonRecordService.employerOf(researched(), List.of()))
                .isEqualTo(new PersonEmployer("Al Rawabi Dairy", null, RESEARCHED_LOGO));
    }

    private static Person researched() {
        Person person = Person.founded(UUID.randomUUID(), UUID.randomUUID(), CandidateSource.EXTENSION,
                details(null));
        person.enrich(RESEARCH);
        return person;
    }

    private static Candidate mappedAt(Person person, UUID triageCompanyId, String companyName) {
        return Candidate.mapped(UUID.randomUUID(), UUID.randomUUID(), triageCompanyId, person,
                CandidateSource.MANUAL, details(companyName));
    }

    private static CandidateDetails details(String employerName) {
        return new CandidateDetails("Sample Person", null, null, CandidateStatus.IDENTIFIED, employerName,
                null, null, "https://www.linkedin.com/in/sample-profile", null, null, null, null,
                null, null, null, CandidateCompensation.unknown(),
                new CandidateProfile(null, null, null, null, null, null), null);
    }
}
