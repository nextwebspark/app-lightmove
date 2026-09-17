package app.lightmove.api.report.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The one ranking every chapter uses: most first, first-seen first among equals, and the tail summed. */
class TallyTest {

    @Test
    @DisplayName("ranks by count, breaks ties by first appearance, and sums what the cut leaves out")
    void ranksAndSumsTheTail() {
        Tally<String> tally = new Tally<>();
        List.of("retail", "fmcg", "fmcg", "agri", "retail", "fmcg", "other").forEach(tally::add);

        List<String> leading = tally.top(2);

        assertThat(leading).containsExactly("fmcg", "retail");
        assertThat(tally.outside(leading)).isEqualTo(2);
        assertThat(tally.outside(List.of())).isEqualTo(7);
        assertThat(tally.of("agri")).isEqualTo(1);
        assertThat(tally.of("nothing")).isZero();
    }

    @Test
    @DisplayName("an empty tally has no leaders and nothing outside them")
    void emptyTally() {
        Tally<String> tally = new Tally<>();

        assertThat(tally.top(3)).isEmpty();
        assertThat(tally.outside(List.of())).isZero();
    }
}
