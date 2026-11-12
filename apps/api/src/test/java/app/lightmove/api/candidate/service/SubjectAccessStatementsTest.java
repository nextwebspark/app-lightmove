package app.lightmove.api.candidate.service;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.candidate.constant.CandidateSource;
import app.lightmove.api.candidate.constant.ContactSource;
import app.lightmove.api.candidate.constant.EnrichmentVendor;
import app.lightmove.api.candidate.model.SubjectAccessAbout;
import app.lightmove.api.candidate.model.SubjectAccessPosition;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SubjectAccessStatementsTest {

    @Test
    @DisplayName("every door a record, a contact or a lookup can come through is explained")
    void everySourceIsExplained() {
        Stream<String> stored = Stream.of(
                Stream.of(CandidateSource.values()).map(CandidateSource::value),
                Stream.of(ContactSource.values()).map(ContactSource::value),
                Stream.of(EnrichmentVendor.values()).map(vendor -> vendor.name().toLowerCase(Locale.ROOT)))
                .flatMap(values -> values);

        assertThat(stored).allSatisfy(value -> assertThat(SubjectAccessStatements.SOURCES).containsKey(value));
    }

    @Test
    @DisplayName("a hiring side is a recipient only while the person is on its position, and in the past once archived")
    void recipientsFollowThePositions() {
        Map<String, SubjectAccessPosition> positions = new LinkedHashMap<>();
        positions.put("open", new SubjectAccessPosition("Chief Financial Officer", "Finance Unit", null));
        positions.put("archived", new SubjectAccessPosition("Chief Operating Officer", "Operations Unit",
                "2026-10-01T08:30:00Z"));
        positions.put("left", new SubjectAccessPosition("Chief Risk Officer", "Risk Unit", null));

        SubjectAccessAbout about = SubjectAccessStatements.aboutOf(
                new SubjectAccessStatements.Firm("Answering Firm", false, 24), positions, Set.of("open", "archived"),
                30);

        assertThat(about.recipients()).anySatisfy(line -> assertThat(line)
                .startsWith("The business unit Finance Unit, for the position Chief Financial Officer")
                .contains("They read your profile on it"));
        assertThat(about.recipients()).anySatisfy(line -> assertThat(line)
                .startsWith("The business unit Operations Unit")
                .contains("While the position was open, until 2026-10-01")
                .contains("they no longer can"));
        assertThat(String.join("\n", about.recipients())).doesNotContain("Risk Unit");
        assertThat(about.retention()).contains("24 months");
        assertThat(about.deletedNotes()).contains("30 days");
    }

    @Test
    @DisplayName("an agency names its clients, and a firm with no retention period keeps records until deleted")
    void agencyWordingAndNoRetention() {
        SubjectAccessAbout about = SubjectAccessStatements.aboutOf(
                new SubjectAccessStatements.Firm("Search Partners", true, null),
                Map.of("open", new SubjectAccessPosition("Chief Executive Officer", "Acme", null)), Set.of("open"),
                30);

        assertThat(about.purposes()).contains("for its clients");
        assertThat(about.recipients()).anySatisfy(line -> assertThat(line).startsWith("The client Acme"));
        assertThat(about.retention()).isEqualTo("Search Partners keeps records until they are deleted.");
    }
}
