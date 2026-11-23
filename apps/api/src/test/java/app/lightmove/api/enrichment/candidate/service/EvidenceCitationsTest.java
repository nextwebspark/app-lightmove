package app.lightmove.api.enrichment.candidate.service;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.enrichment.candidate.service.EvidenceCitations.CitedCall;
import app.lightmove.api.enrichment.candidate.service.EvidenceCitations.WrittenDay;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** #1326: what a sentence of evidence cites, however the model spells it. */
class EvidenceCitationsTest {

    @Test
    @DisplayName("a day reads the same in full, in short and in capitals")
    void aDayIsOneDayHoweverItIsWritten() {
        assertThat(EvidenceCitations.daysIn("On 14 October 2026, then 14 OCT 2026 and 4 sept 2026."))
                .extracting(WrittenDay::day).containsExactly("14 Oct 2026", "14 Oct 2026", "4 Sep 2026");
        assertThat(EvidenceCitations.written(LocalDate.of(2026, 10, 14))).isEqualTo("14 Oct 2026");
    }

    @Test
    @DisplayName("a call is its day and the time into it, in either form the model writes")
    void aCallIsItsDayAndTime() {
        assertThat(EvidenceCitations.callsIn("call 12 Oct 2026, 14:05 in; interview on 3 NOV 2026 (1:02:03 in)"))
                .containsExactly(new CitedCall("12 Oct 2026", 845), new CitedCall("3 Nov 2026", 3723));
        assertThat(EvidenceCitations.callsIn("the call on 12 Oct 2026")).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Nothing on file bears on this.", "There is no evidence on file of board work.",
            "No direct evidence of treasury work.", "Not enough information to judge.",
            "Insufficient evidence in the material.", "Board work is not mentioned anywhere."})
    @DisplayName("says nothing is on file")
    void saysNothingIsOnFile(String evidence) {
        assertThat(EvidenceCitations.saysNothingIsOnFile(evidence)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Led the turnaround with no outside hires.", "None of her peers did this.",
            "She left nothing to chance in the refinancing."})
    @DisplayName("a no about something else is not an empty file")
    void aBareNoIsNotAnEmptyFile(String evidence) {
        assertThat(EvidenceCitations.saysNothingIsOnFile(evidence)).isFalse();
    }

    @Test
    @DisplayName("names the profile, a note by its day, a call by its time and a document by its title's words")
    void namesEachSource() {
        List<String> titles = List.of("board paper march 2024");
        Set<String> noteDays = Set.of("14 Oct 2026");

        assertThat(EvidenceCitations.sourcesNamedIn("Inferred from her career.", titles, noteDays))
                .containsExactly(EvidenceCitations.PROFILE);
        assertThat(EvidenceCitations.sourcesNamedIn("Recorded on 14 OCT 2026.", titles, noteDays))
                .containsExactly(EvidenceCitations.NOTE);
        assertThat(EvidenceCitations.sourcesNamedIn("On the call 20 Oct 2026, 03:10 in.", titles, noteDays))
                .containsExactly(EvidenceCitations.CALL);
        assertThat(EvidenceCitations.sourcesNamedIn("The March 2024 board paper sets it out.", titles, noteDays))
                .containsExactly(EvidenceCitations.DOCUMENT);
        assertThat(EvidenceCitations.sourcesNamedIn("She moved both teams in four months.", titles, noteDays))
                .isEmpty();
        assertThat(EvidenceCitations.sourcesNamedIn(null, titles, noteDays)).isEmpty();
    }
}
