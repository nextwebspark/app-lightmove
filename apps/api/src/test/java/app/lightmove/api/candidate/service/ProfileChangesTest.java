package app.lightmove.api.candidate.service;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.candidate.constant.CandidateSource;
import app.lightmove.api.candidate.constant.ContactSource;
import app.lightmove.api.candidate.constant.Mobility;
import app.lightmove.api.candidate.model.CandidateCompensation;
import app.lightmove.api.candidate.model.CandidateDetails;
import app.lightmove.api.candidate.model.CandidateProfile;
import app.lightmove.api.candidate.model.CompensationBreakdown;
import app.lightmove.api.candidate.model.MoveExpectations;
import app.lightmove.api.candidate.model.Person;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** What a profile save tells the timeline about what an executive expects of a move. */
class ProfileChangesTest {

    private static final CandidateCompensation IN_AED = new CandidateCompensation("AED", 1_500_000L, null, null,
            null, "3 months", CompensationBreakdown.empty());

    @Test
    @DisplayName("what they expect reads in the drawer's words, the figures in the package's currency")
    void expectationsReadInTheDrawersWords() {
        Person person = personExpecting(MoveExpectations.unknown());
        ProfileChanges changes = ProfileChanges.before(person);

        person.describe(details(new MoveExpectations(1_700_000L, 4_000_000L, null, Mobility.REGION, null,
                "Jan 2027")), ContactSource.MANUAL);

        assertThat(changesOf(changes.after(person))).containsOnly(
                Map.entry("expectedBaseSalary", "AED 1,700,000"),
                Map.entry("expectedTotalPackage", "AED 4,000,000"),
                Map.entry("mobility", "Open within the region"),
                Map.entry("availableFrom", "Jan 2027"));
    }

    @Test
    @DisplayName("an expectation note that quotes a phone number is kept with the number taken out")
    void expectationNotesAreRedacted() {
        Person person = personExpecting(MoveExpectations.unknown());
        ProfileChanges changes = ProfileChanges.before(person);

        person.describe(details(new MoveExpectations(null, null, "Call me on +971 50 123 4567 about the LTIP",
                null, "Family stays in Abu Dhabi", null)), ContactSource.MANUAL);

        Map<String, String> recorded = changesOf(changes.after(person));
        assertThat(recorded.get("expectedPackageNote")).doesNotContain("123 4567").contains("[phone removed]")
                .contains("about the LTIP");
        assertThat(recorded.get("mobilityNote")).isEqualTo("Family stays in Abu Dhabi");
    }

    @Test
    @DisplayName("clearing what they expect is a change to nothing, not a change that went unrecorded")
    void clearingIsRecorded() {
        Person person = personExpecting(new MoveExpectations(null, null, null, Mobility.NONE, null, null));
        ProfileChanges changes = ProfileChanges.before(person);

        person.describe(details(MoveExpectations.unknown()), ContactSource.MANUAL);

        List<Map<String, String>> recorded = changes.after(person);
        assertThat(recorded).hasSize(1);
        assertThat(recorded.getFirst()).containsEntry("field", "mobility")
                .containsEntry("from", "Not open to relocating").containsEntry("to", null);
    }

    private static Person personExpecting(MoveExpectations expectations) {
        return Person.founded(UUID.randomUUID(), UUID.randomUUID(), CandidateSource.MANUAL, details(expectations));
    }

    private static CandidateDetails details(MoveExpectations expectations) {
        return new CandidateDetails("Sample Person", null, null, null, null, null, null, null, null, null, null,
                null, null, null, IN_AED, expectations, CandidateProfile.empty(), null);
    }

    private static Map<String, String> changesOf(List<Map<String, String>> changes) {
        Map<String, String> byField = new LinkedHashMap<>();
        changes.forEach(change -> byField.put(change.get("field"), change.get("to")));
        return byField;
    }
}
