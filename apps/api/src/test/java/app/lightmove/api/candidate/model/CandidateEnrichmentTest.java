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
 * The two rules enrichment lives by: vendor data never outranks a researcher, and a drawer edit never
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
        Candidate candidate = captured(details("Sample Person", null, null, null, null, null));

        candidate.enrich(RESEARCH);

        assertThat(candidate.getTitle()).isEqualTo("Group CFO");
        assertThat(candidate.getSummary()).isEqualTo("Finance leader across GCC retail.");
        assertThat(candidate.getLocationCity()).isEqualTo("Dubai");
        assertThat(candidate.getLocationCountry()).isEqualTo("United Arab Emirates");
        assertThat(candidate.getCompanyName()).isEqualTo("Al Rawabi Dairy");
        assertThat(candidate.getProfile().career()).hasSize(1);
        assertThat(candidate.getProfile().languages()).containsExactly("English", "Arabic");
        assertThat(candidate.getProfile().education()).hasSize(1);
        assertThat(candidate.getProfile().skills()).containsExactly("Financial Planning");
        assertThat(candidate.getProfile().enrichedAt()).isNotNull();
        assertThat(candidate.getEnrichedBy()).isEqualTo(EnrichmentVendor.BRIGHTDATA);
    }

    @Test
    @DisplayName("an unresearched candidate names no provider")
    void anUnresearchedCandidateNamesNoProvider() {
        Candidate candidate = captured(details("Sample Person", null, null, null, null, null));

        assertThat(candidate.getEnrichedBy()).isNull();
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
                        List.of("French"), null, null, null),
                null);
        Candidate candidate = captured(typed);

        candidate.enrich(RESEARCH);

        assertThat(candidate.getTitle()).isEqualTo("CFO, as we met them");
        assertThat(candidate.getSummary()).isEqualTo("Our own read of them.");
        assertThat(candidate.getLocationCity()).isEqualTo("Abu Dhabi");
        assertThat(candidate.getCompanyName()).isEqualTo("The Firm They Told Us");
        assertThat(candidate.getProfile().career().getFirst().company())
                .isEqualTo("The Firm They Told Us");
        assertThat(candidate.getProfile().languages()).containsExactly("French");
        // The fields only research writes still land.
        assertThat(candidate.getProfile().education()).hasSize(1);
        assertThat(candidate.getProfile().skills()).containsExactly("Financial Planning");
    }

    @Test
    @DisplayName("a proposed background fills the empty fields and flags each one")
    void aProposedBackgroundIsFilledAndFlagged() {
        Candidate candidate = captured(details("Sample Person", null, null, null, null, null));

        assertThat(candidate.proposeBackground(PROPOSED)).isTrue();

        assertThat(candidate.getGender()).isEqualTo(Gender.FEMALE);
        assertThat(candidate.getYearsExperience()).isEqualTo(14);
        assertThat(candidate.getSeniorityLevel()).isEqualTo(Seniority.N_MINUS_1);
        assertThat(candidate.getNationality()).isNull();
        assertThat(candidate.getAiInferredFields()).containsExactlyInAnyOrder(
                "gender", "yearsExperience", "seniority");
    }

    @Test
    @DisplayName("a proposal never overwrites a background already on the row, and flags nothing")
    void aProposalNeverOverwritesAnExistingBackground() {
        Candidate candidate = captured(detailsWithBackground("Omani", Gender.MALE, 20, Seniority.C_SUITE));

        assertThat(candidate.proposeBackground(PROPOSED)).isFalse();
        candidate.recordNationalityReading(DECISIVE);

        assertThat(candidate.getNationality()).isEqualTo("Omani");
        assertThat(candidate.getGender()).isEqualTo(Gender.MALE);
        assertThat(candidate.getYearsExperience()).isEqualTo(20);
        assertThat(candidate.getSeniorityLevel()).isEqualTo(Seniority.C_SUITE);
        assertThat(candidate.getAiInferredFields()).isEmpty();
        assertThat(candidate.getAiNationalityReading()).isEqualTo(DECISIVE);
    }

    @Test
    @DisplayName("a researcher changing an inferred value clears its flag")
    void editingAnInferredValueClearsItsFlag() {
        Candidate candidate = captured(details("Sample Person", null, null, null, null, null));
        candidate.proposeBackground(PROPOSED);
        candidate.recordNationalityReading(DECISIVE);

        candidate.describe(detailsWithBackground("Western expat", Gender.FEMALE, 14, Seniority.N_MINUS_2),
                ContactSource.MANUAL);

        // Nationality and seniority changed; gender and yearsExperience were resubmitted unchanged, so
        // nothing about them was actually reviewed and their flags stand.
        assertThat(candidate.getAiInferredFields()).containsExactlyInAnyOrder("gender", "yearsExperience");
    }

    @Test
    @DisplayName("resubmitting an inferred value unchanged leaves it flagged")
    void resubmittingTheSameInferredValueLeavesItFlagged() {
        Candidate candidate = captured(details("Sample Person", null, null, null, null, null));
        candidate.proposeBackground(PROPOSED);
        candidate.recordNationalityReading(DECISIVE);

        candidate.describe(detailsWithBackground("Emirati", Gender.FEMALE, 14, Seniority.N_MINUS_1),
                ContactSource.MANUAL);

        assertThat(candidate.getAiInferredFields()).containsExactlyInAnyOrder(
                "nationality", "gender", "yearsExperience", "seniority");
    }

    @Test
    @DisplayName("saving the Background section confirms every AI-proposed value in it")
    void confirmingTheBackgroundClearsEveryFlag() {
        Candidate candidate = captured(details("Sample Person", null, null, null, null, null));
        candidate.proposeBackground(PROPOSED);
        candidate.recordNationalityReading(DECISIVE);

        candidate.confirmBackground();

        assertThat(candidate.getAiInferredFields()).isEmpty();
        assertThat(candidate.getNationality()).isEqualTo("Emirati");
        assertThat(candidate.getSeniorityLevel()).isEqualTo(Seniority.N_MINUS_1);
    }

    @Test
    @DisplayName("a successful assessment supersedes an earlier failed run")
    void anAssessmentClearsAnEarlierFailure() {
        Candidate candidate = captured(details("Sample Person", null, null, null, null, null));
        candidate.recordAiEnrichFailure();
        assertThat(candidate.getAiEnrichFailedAt()).isNotNull();

        candidate.recordAiAssessment(new CandidateAiAssessment("Read.", null, null, "2026-09-25T10:00:00Z"));

        assertThat(candidate.getAiEnrichFailedAt()).isNull();
    }

    @Test
    @DisplayName("only the fields still empty are named as missing")
    void missingBackgroundNamesOnlyTheEmptyFields() {
        Candidate candidate = captured(detailsWithBackground(null, null, 20, null));

        assertThat(candidate.missingBackground()).containsExactlyInAnyOrder(
                BackgroundField.NATIONALITY, BackgroundField.GENDER, BackgroundField.SENIORITY);
    }

    @Test
    @DisplayName("a high-confidence nationality reading fills the empty field and flags it")
    void aDecisiveReadingFillsAndFlags() {
        Candidate candidate = captured(details("Sample Person", null, null, null, null, null));

        candidate.recordNationalityReading(DECISIVE);

        assertThat(candidate.getNationality()).isEqualTo("Emirati");
        assertThat(candidate.getAiInferredFields()).containsExactly("nationality");
        assertThat(candidate.getAiNationalityReading()).isEqualTo(DECISIVE);
    }

    @Test
    @DisplayName("a medium, low or Unknown reading is kept as a suggestion and never fills the field")
    void anIndecisiveReadingIsOnlyKept() {
        for (NationalityReading reading : List.of(reading("Emirati", "medium"),
                reading("Emirati", "low"), reading(NationalityReading.UNKNOWN, "high"))) {
            Candidate candidate = captured(details("Sample Person", null, null, null, null, null));

            candidate.recordNationalityReading(reading);

            assertThat(candidate.getNationality()).isNull();
            assertThat(candidate.getAiInferredFields()).isEmpty();
            assertThat(candidate.getAiNationalityReading()).isEqualTo(reading);
        }
    }

    @Test
    @DisplayName("a mapped candidate's employer is the triage snapshot, never the vendor's answer")
    void aMappedCandidateKeepsTheSnapshotEmployer() {
        Candidate candidate = Candidate.mapped(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), CandidateSource.EXTENSION,
                details("Sample Person", null, "Snapshot Co", null, null, null));

        candidate.enrich(RESEARCH);

        assertThat(candidate.getCompanyName()).isEqualTo("Snapshot Co");
    }

    @Test
    @DisplayName("a drawer edit replaces what it renders and carries the rest of the profile along")
    void aDrawerEditKeepsEnrichment() {
        Candidate candidate = captured(details("Sample Person", null, null, null, null, null));
        candidate.enrich(RESEARCH);
        String enrichedAt = candidate.getProfile().enrichedAt();

        candidate.describe(details("Sample Person", "CFO", null, null,
                List.of(new CandidateCareerEntry("Corrected Employer", "CFO", "2020 –", null)),
                List.of("English")), ContactSource.MANUAL);

        assertThat(candidate.getProfile().career().getFirst().company()).isEqualTo("Corrected Employer");
        assertThat(candidate.getProfile().languages()).containsExactly("English");
        // The regression this guards: the drawer resubmits only what it renders, and a wholesale
        // profile replace wiped these on the first edit after enrichment.
        assertThat(candidate.getProfile().education()).hasSize(1);
        assertThat(candidate.getProfile().skills()).containsExactly("Financial Planning");
        assertThat(candidate.getProfile().enrichedAt()).isEqualTo(enrichedAt);
    }

    @Test
    @DisplayName("research resolving the employer maps the person and snapshots the name")
    void employByMapsAndSnapshots() {
        Candidate candidate = captured(details("Sample Person", null, null, null, null, null));
        UUID companyId = UUID.randomUUID();

        candidate.employBy(companyId, "Al Rawabi Dairy");

        assertThat(candidate.getTriageCompanyId()).isEqualTo(companyId);
        assertThat(candidate.getCompanyName()).isEqualTo("Al Rawabi Dairy");
    }

    private static Candidate captured(CandidateDetails details) {
        return Candidate.mapped(UUID.randomUUID(), UUID.randomUUID(), null,
                CandidateSource.EXTENSION, details);
    }

    private static CandidateDetails details(String fullName, String title, String employerName,
                                            String summary, List<CandidateCareerEntry> career,
                                            List<String> languages) {
        return new CandidateDetails(fullName, title, null, CandidateStatus.IDENTIFIED, employerName,
                null, null, "https://www.linkedin.com/in/sample-profile", null, null, null, null,
                null, summary, null, CandidateCompensation.unknown(),
                new CandidateProfile(career, languages, null, null, null), null);
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
