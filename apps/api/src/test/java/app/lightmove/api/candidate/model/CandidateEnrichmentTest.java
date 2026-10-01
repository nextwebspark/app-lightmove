package app.lightmove.api.candidate.model;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.candidate.constant.BackgroundField;
import app.lightmove.api.candidate.constant.CandidateSource;
import app.lightmove.api.candidate.constant.CandidateStatus;
import app.lightmove.api.candidate.constant.ContactSource;
import app.lightmove.api.candidate.constant.EnrichmentVendor;
import app.lightmove.api.candidate.constant.Gender;
import app.lightmove.api.common.constant.Seniority;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The two rules enrichment lives by, on the person it researches: vendor data never outranks a researcher, and a drawer edit never
 * wipes the fields the drawer has never heard of.
 */
class CandidateEnrichmentTest {

    private static final EnrichedProfile RESEARCH = new EnrichedProfile(
            "Group CFO", "Finance leader across GCC retail.", "Al Rawabi Dairy",
            "https://www.linkedin.com/company/alrawabi/", "https://media.example.com/alrawabi.png",
            "Dubai", "United Arab Emirates",
            List.of(new CandidateCareerEntry("Al Rawabi Dairy", "Group CFO", "2021 – Present", null)),
            List.of(new CandidateEducationEntry("AUC", "MBA, Finance", "2010 - 2012")),
            List.of("Financial Planning"), List.of("English", "Arabic"), null,
            EnrichmentVendor.BRIGHTDATA);

    private static final InferredBackground PROPOSED = new InferredBackground(Gender.FEMALE, 14, Seniority.N_MINUS_1);

    private static final NationalityReading DECISIVE = reading("Emirati", "high");

    @Test
    @DisplayName("research fills in what nobody typed")
    void researchFillsWhatNobodyTyped() {
        Person person = captured(details("Sample Person", null, null, null, null, null));

        person.enrich(RESEARCH);

        assertThat(person.getTitle()).isEqualTo("Group CFO");
        assertThat(person.getSummary()).isEqualTo("Finance leader across GCC retail.");
        assertThat(person.getLocationCity()).isEqualTo("Dubai");
        assertThat(person.getLocationCountry()).isEqualTo("United Arab Emirates");
        assertThat(person.getProfile().career()).hasSize(1);
        assertThat(person.getProfile().languages()).containsExactly("English", "Arabic");
        assertThat(person.getProfile().education()).hasSize(1);
        assertThat(person.getProfile().skills()).containsExactly("Financial Planning");
        assertThat(person.getProfile().enrichedAt()).isNotNull();
        assertThat(person.getEnrichedBy()).isEqualTo(EnrichmentVendor.BRIGHTDATA);
    }

    @Test
    @DisplayName("an unresearched candidate names no provider")
    void anUnresearchedCandidateNamesNoProvider() {
        Person person = captured(details("Sample Person", null, null, null, null, null));

        assertThat(person.getEnrichedBy()).isNull();
    }

    @Test
    @DisplayName("research never overwrites what a researcher already wrote")
    void researchNeverOverwritesTheResearcher() {
        CandidateDetails typed = new CandidateDetails("Sample Person", "CFO, as we met them",
                null, CandidateStatus.IDENTIFIED, "The Firm They Told Us", null,
                null, null, "UAE", "Abu Dhabi", null, null, null, "Our own read of them.", null,
                CandidateCompensation.unknown(),
                new CandidateProfile(
                        List.of(new CandidateCareerEntry("The Firm They Told Us", "CFO", "2019 –", null)),
                        List.of("French"), null, null, null, null),
                null);
        Person person = captured(typed);

        person.enrich(RESEARCH);

        assertThat(person.getTitle()).isEqualTo("CFO, as we met them");
        assertThat(person.getSummary()).isEqualTo("Our own read of them.");
        assertThat(person.getLocationCity()).isEqualTo("Abu Dhabi");
        assertThat(person.getProfile().career().getFirst().company())
                .isEqualTo("The Firm They Told Us");
        assertThat(person.getProfile().languages()).containsExactly("French");
        // The fields only research writes still land.
        assertThat(person.getProfile().education()).hasSize(1);
        assertThat(person.getProfile().skills()).containsExactly("Financial Planning");
    }

    @Test
    @DisplayName("a proposed background fills the empty fields and flags each one")
    void aProposedBackgroundIsFilledAndFlagged() {
        Person person = captured(details("Sample Person", null, null, null, null, null));

        assertThat(person.proposeBackground(PROPOSED)).isTrue();

        assertThat(person.getGender()).isEqualTo(Gender.FEMALE);
        assertThat(person.getYearsExperience()).isEqualTo(14);
        assertThat(person.getSeniorityLevel()).isEqualTo(Seniority.N_MINUS_1);
        assertThat(person.getNationality()).isNull();
        assertThat(person.getAiInferredFields()).containsExactlyInAnyOrder(
                "gender", "yearsExperience", "seniority");
    }

    @Test
    @DisplayName("a proposal never overwrites a background already on the row, and flags nothing")
    void aProposalNeverOverwritesAnExistingBackground() {
        Person person = captured(detailsWithBackground("Omani", Gender.MALE, 20, Seniority.C_SUITE));

        assertThat(person.proposeBackground(PROPOSED)).isFalse();
        person.recordNationalityReading(DECISIVE);

        assertThat(person.getNationality()).isEqualTo("Omani");
        assertThat(person.getGender()).isEqualTo(Gender.MALE);
        assertThat(person.getYearsExperience()).isEqualTo(20);
        assertThat(person.getSeniorityLevel()).isEqualTo(Seniority.C_SUITE);
        assertThat(person.getAiInferredFields()).isEmpty();
        assertThat(person.getAiNationalityReading()).isEqualTo(DECISIVE);
    }

    @Test
    @DisplayName("a researcher changing an inferred value clears its flag")
    void editingAnInferredValueClearsItsFlag() {
        Person person = captured(details("Sample Person", null, null, null, null, null));
        person.proposeBackground(PROPOSED);
        person.recordNationalityReading(DECISIVE);

        person.describe(detailsWithBackground("Western expat", Gender.FEMALE, 14, Seniority.N_MINUS_2),
                ContactSource.MANUAL);

        // Nationality and seniority changed; gender and yearsExperience were resubmitted unchanged, so
        // nothing about them was actually reviewed and their flags stand.
        assertThat(person.getAiInferredFields()).containsExactlyInAnyOrder("gender", "yearsExperience");
    }

    @Test
    @DisplayName("resubmitting an inferred value unchanged leaves it flagged")
    void resubmittingTheSameInferredValueLeavesItFlagged() {
        Person person = captured(details("Sample Person", null, null, null, null, null));
        person.proposeBackground(PROPOSED);
        person.recordNationalityReading(DECISIVE);

        person.describe(detailsWithBackground("Emirati", Gender.FEMALE, 14, Seniority.N_MINUS_1),
                ContactSource.MANUAL);

        assertThat(person.getAiInferredFields()).containsExactlyInAnyOrder(
                "nationality", "gender", "yearsExperience", "seniority");
    }

    @Test
    @DisplayName("saving the Background section confirms every AI-proposed value in it")
    void confirmingTheBackgroundClearsEveryFlag() {
        Person person = captured(details("Sample Person", null, null, null, null, null));
        person.proposeBackground(PROPOSED);
        person.recordNationalityReading(DECISIVE);

        person.confirmBackground();

        assertThat(person.getAiInferredFields()).isEmpty();
        assertThat(person.getNationality()).isEqualTo("Emirati");
        assertThat(person.getSeniorityLevel()).isEqualTo(Seniority.N_MINUS_1);
    }

    @Test
    @DisplayName("a successful assessment supersedes an earlier failed run")
    void anAssessmentClearsAnEarlierFailure() {
        Candidate candidate = onMandate(null, details("Sample Person", null, null, null, null, null));
        candidate.recordAiEnrichFailure();
        assertThat(candidate.getAiEnrichFailedAt()).isNotNull();

        candidate.recordAiAssessment(new CandidateAiAssessment("Read.", null, null, "2026-09-25T10:00:00Z"));

        assertThat(candidate.getAiEnrichFailedAt()).isNull();
    }

    @Test
    @DisplayName("only the fields still empty are named as missing")
    void missingBackgroundNamesOnlyTheEmptyFields() {
        Person person = captured(detailsWithBackground(null, null, 20, null));

        assertThat(person.missingBackground()).containsExactlyInAnyOrder(
                BackgroundField.NATIONALITY, BackgroundField.GENDER, BackgroundField.SENIORITY);
    }

    @Test
    @DisplayName("a high-confidence nationality reading fills the empty field and flags it")
    void aDecisiveReadingFillsAndFlags() {
        Person person = captured(details("Sample Person", null, null, null, null, null));

        person.recordNationalityReading(DECISIVE);

        assertThat(person.getNationality()).isEqualTo("Emirati");
        assertThat(person.getAiInferredFields()).containsExactly("nationality");
        assertThat(person.getAiNationalityReading()).isEqualTo(DECISIVE);
    }

    @Test
    @DisplayName("a medium, low or Unknown reading is kept as a suggestion and never fills the field")
    void anIndecisiveReadingIsOnlyKept() {
        for (NationalityReading reading : List.of(reading("Emirati", "medium"),
                reading("Emirati", "low"), reading(NationalityReading.UNKNOWN, "high"))) {
            Person person = captured(details("Sample Person", null, null, null, null, null));

            person.recordNationalityReading(reading);

            assertThat(person.getNationality()).isNull();
            assertThat(person.getAiInferredFields()).isEmpty();
            assertThat(person.getAiNationalityReading()).isEqualTo(reading);
        }
    }

    @Test
    @DisplayName("research names the employer for a mandate that recorded none")
    void researchNamesAnUnrecordedEmployer() {
        Candidate candidate = onMandate(null, details("Sample Person", null, null, null, null, null));

        candidate.adoptEmployer(RESEARCH.employerName());

        assertThat(candidate.getCompanyName()).isEqualTo("Al Rawabi Dairy");
    }

    @Test
    @DisplayName("research never replaces the employer a researcher typed")
    void researchKeepsATypedEmployer() {
        Candidate candidate = onMandate(null, details("Sample Person", null, "The Firm They Told Us", null, null, null));

        candidate.adoptEmployer(RESEARCH.employerName());

        assertThat(candidate.getCompanyName()).isEqualTo("The Firm They Told Us");
    }

    @Test
    @DisplayName("a mapped candidate's employer is the triage snapshot, never the vendor's answer")
    void aMappedCandidateKeepsTheSnapshotEmployer() {
        Candidate candidate = onMandate(UUID.randomUUID(),
                details("Sample Person", null, "Snapshot Co", null, null, null));

        candidate.adoptEmployer(RESEARCH.employerName());

        assertThat(candidate.getCompanyName()).isEqualTo("Snapshot Co");
    }

    @Test
    @DisplayName("a drawer edit replaces what it renders and carries the rest of the profile along")
    void aDrawerEditKeepsEnrichment() {
        Person person = captured(details("Sample Person", null, null, null, null, null));
        person.enrich(RESEARCH);
        String enrichedAt = person.getProfile().enrichedAt();

        person.describe(details("Sample Person", "CFO", null, null,
                List.of(new CandidateCareerEntry("Corrected Employer", "CFO", "2020 –", null)),
                List.of("English")), ContactSource.MANUAL);

        assertThat(person.getProfile().career().getFirst().company()).isEqualTo("Corrected Employer");
        assertThat(person.getProfile().languages()).containsExactly("English");
        // The regression this guards: the drawer resubmits only what it renders, and a wholesale
        // profile replace wiped these on the first edit after enrichment.
        assertThat(person.getProfile().education()).hasSize(1);
        assertThat(person.getProfile().skills()).containsExactly("Financial Planning");
        assertThat(person.getProfile().enrichedAt()).isEqualTo(enrichedAt);
        assertThat(person.getProfile().employer()).isEqualTo(new ResearchedEmployerMark("Al Rawabi Dairy",
                "alrawabi", "https://media.example.com/alrawabi.png"));
    }

    @Test
    @DisplayName("research resolving the employer maps the person and snapshots the name")
    void employByMapsAndSnapshots() {
        Candidate candidate = onMandate(null, details("Sample Person", null, null, null, null, null));
        UUID companyId = UUID.randomUUID();

        candidate.employBy(companyId, "Al Rawabi Dairy");

        assertThat(candidate.getTriageCompanyId()).isEqualTo(companyId);
        assertThat(candidate.getCompanyName()).isEqualTo("Al Rawabi Dairy");
    }

    private static Person captured(CandidateDetails details) {
        return Person.founded(UUID.randomUUID(), UUID.randomUUID(), CandidateSource.EXTENSION, details);
    }

    private static Candidate onMandate(UUID triageCompanyId, CandidateDetails details) {
        return Candidate.mapped(UUID.randomUUID(), UUID.randomUUID(), triageCompanyId, captured(details),
                CandidateSource.EXTENSION, details);
    }

    private static CandidateDetails details(String fullName, String title, String employerName,
                                            String summary, List<CandidateCareerEntry> career,
                                            List<String> languages) {
        return new CandidateDetails(fullName, title, null, CandidateStatus.IDENTIFIED, employerName,
                null, null, "https://www.linkedin.com/in/sample-profile", null, null, null, null,
                null, summary, null, CandidateCompensation.unknown(),
                new CandidateProfile(career, languages, null, null, null, null), null);
    }

    private static NationalityReading reading(String category, String confidence) {
        return new NationalityReading(category, confidence, List.of("evidence"), List.of(), "none",
                "2026-09-27T10:00:00Z");
    }

    private static CandidateDetails detailsWithBackground(String nationality, Gender gender,
                                                           Integer yearsExperience, Seniority seniority) {
        return new CandidateDetails("Sample Person", null, seniority, CandidateStatus.IDENTIFIED, null,
                null, null, "https://www.linkedin.com/in/sample-profile", null, null, nationality,
                gender, yearsExperience, null, null, CandidateCompensation.unknown(),
                CandidateProfile.empty(), null);
    }
}
