package app.lightmove.api.candidate.model;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.candidate.constant.CandidateSource;
import app.lightmove.api.candidate.constant.CandidateStatus;
import app.lightmove.api.candidate.constant.ContactSource;
import app.lightmove.api.candidate.constant.EnrichmentVendor;
import app.lightmove.api.candidate.constant.Gender;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The two rules enrichment lives by: vendor data never outranks a researcher, and a drawer edit never
 * wipes the fields the drawer has never heard of.
 */
class CandidateEnrichmentTest {

    private static final EnrichedProfile RESEARCH = EnrichedProfile.researched(
            "Group CFO", "Finance leader across GCC retail.", "Al Rawabi Dairy",
            "https://www.linkedin.com/company/alrawabi/", "https://media.example.com/alrawabi.png",
            "Dubai", "United Arab Emirates",
            List.of(new CandidateCareerEntry("Al Rawabi Dairy", "Group CFO", "2021 – Present")),
            List.of(new CandidateEducationEntry("AUC", "MBA, Finance", "2010 - 2012")),
            List.of("Financial Planning"), List.of("English", "Arabic"), null,
            EnrichmentVendor.BRIGHTDATA);

    private static final EnrichedProfile RESEARCH_WITH_BACKGROUND =
            RESEARCH.withBackground("Emirati", Gender.FEMALE, 14);

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
                        List.of(new CandidateCareerEntry("The Firm They Told Us", "CFO", "2019 –")),
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
    @DisplayName("an inferred background is filled in and flagged, not just recorded")
    void researchProposesBackgroundAndFlagsItInferred() {
        Candidate candidate = captured(details("Sample Person", null, null, null, null, null));

        candidate.enrich(RESEARCH_WITH_BACKGROUND);

        assertThat(candidate.getNationality()).isEqualTo("Emirati");
        assertThat(candidate.getGender()).isEqualTo(Gender.FEMALE);
        assertThat(candidate.getYearsExperience()).isEqualTo(14);
        assertThat(candidate.getAiInferredFields()).containsExactlyInAnyOrder(
                "nationality", "gender", "yearsExperience");
    }

    @Test
    @DisplayName("research never overwrites a background a researcher already typed, and flags nothing")
    void researchNeverOverwritesTheResearchersBackground() {
        Candidate candidate = captured(detailsWithBackground("Emirati", Gender.MALE, 20));

        candidate.enrich(RESEARCH_WITH_BACKGROUND);

        assertThat(candidate.getNationality()).isEqualTo("Emirati");
        assertThat(candidate.getGender()).isEqualTo(Gender.MALE);
        assertThat(candidate.getYearsExperience()).isEqualTo(20);
        assertThat(candidate.getAiInferredFields()).isEmpty();
    }

    @Test
    @DisplayName("a researcher changing an inferred value clears its flag")
    void editingAnInferredValueClearsItsFlag() {
        Candidate candidate = captured(details("Sample Person", null, null, null, null, null));
        candidate.enrich(RESEARCH_WITH_BACKGROUND);

        candidate.describe(detailsWithBackground("Western expat", Gender.FEMALE, 14), ContactSource.MANUAL);

        // Only nationality changed (Emirati -> Western expat); gender and yearsExperience were
        // resubmitted unchanged, so nothing about them was actually reviewed and their flags stand.
        assertThat(candidate.getAiInferredFields()).containsExactlyInAnyOrder("gender", "yearsExperience");
    }

    @Test
    @DisplayName("resubmitting an inferred value unchanged leaves it flagged")
    void resubmittingTheSameInferredValueLeavesItFlagged() {
        Candidate candidate = captured(details("Sample Person", null, null, null, null, null));
        candidate.enrich(RESEARCH_WITH_BACKGROUND);

        candidate.describe(detailsWithBackground("Emirati", Gender.FEMALE, 14), ContactSource.MANUAL);

        assertThat(candidate.getAiInferredFields()).containsExactlyInAnyOrder(
                "nationality", "gender", "yearsExperience");
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
                List.of(new CandidateCareerEntry("Corrected Employer", "CFO", "2020 –")),
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

    private static CandidateDetails detailsWithBackground(String nationality, Gender gender,
                                                           Integer yearsExperience) {
        return new CandidateDetails("Sample Person", null, null, CandidateStatus.IDENTIFIED, null,
                null, null, "https://www.linkedin.com/in/sample-profile", null, null, nationality,
                gender, yearsExperience, null, null, CandidateCompensation.unknown(),
                CandidateProfile.empty(), null);
    }
}
