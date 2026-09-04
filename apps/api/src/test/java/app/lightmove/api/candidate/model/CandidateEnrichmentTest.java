package app.lightmove.api.candidate.model;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.candidate.constant.CandidateSource;
import app.lightmove.api.candidate.constant.CandidateStatus;
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
            List.of(new CandidateCareerEntry("Al Rawabi Dairy", "Group CFO", "2021 – Present")),
            List.of(new CandidateEducationEntry("AUC", "MBA, Finance", "2010 - 2012")),
            List.of("Financial Planning"), List.of("English", "Arabic"), null);

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
    }

    @Test
    @DisplayName("research never overwrites what a researcher already wrote")
    void researchNeverOverwritesTheResearcher() {
        CandidateDetails typed = new CandidateDetails("Sample Person", "CFO, as we met them",
                null, CandidateStatus.IDENTIFIED, "The Firm They Told Us", null,
                null, null, "UAE", "Abu Dhabi", null, null, "Our own read of them.", null,
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
                List.of("English")));

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
                summary, null, CandidateCompensation.unknown(),
                new CandidateProfile(career, languages, null, null, null), null);
    }
}
