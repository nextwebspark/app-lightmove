package app.lightmove.api.enrichment.sourcing.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A title is read as whole words: the headlines a Head of IT search filed at a pharmacy chain on
 * 2 November 2026 all carried "cto" inside a longer word, and none of them names a technology seat.
 */
class TitleWordsTest {

    @Test
    @DisplayName("an abbreviation is found as a word of its own, never inside Director, Doctor or Sector")
    void anAbbreviationIsAWholeWord() {
        List<String> technologyChief = List.of("CTO");

        assertThat(TitleWords.containsAny("CTO, DP World", technologyChief)).isTrue();
        assertThat(TitleWords.containsAny("Group CTO | Board Advisor", technologyChief)).isTrue();
        assertThat(TitleWords.containsAny("Doctor of Pharmacy", technologyChief)).isFalse();
        assertThat(TitleWords.containsAny("DHA pharmacist / Medical director/ senior pharmacist", technologyChief))
                .isFalse();
        assertThat(TitleWords.containsAny("Qualified Dha pharmacist seeking for better opportunities in Health care sector",
                technologyChief)).isFalse();
    }

    @Test
    @DisplayName("case and punctuation are ignored, and a word of four letters or more matches its plural")
    void caseAndPluralsAreIgnored() {
        assertThat(TitleWords.containsAny("Head – Information Technology । CIO", List.of("information")))
                .isTrue();
        assertThat(TitleWords.containsAny("Board of Directors", List.of("Director"))).isTrue();
        assertThat(TitleWords.containsAny("Head of Processes", List.of("Process"))).isTrue();
        assertThat(TitleWords.containsAny("Product owner and its roadmap", List.of("IT"))).isFalse();
        assertThat(TitleWords.containsAny("Head of Finance", List.of("Fin"))).isFalse();
        assertThat(TitleWords.containsAny("Technician", List.of("Tech"))).isFalse();
    }

    @Test
    @DisplayName("a word the model wrote with a hyphen or an ampersand matches the same run of title words")
    void aJoinedWordMatchesItsRun() {
        assertThat(TitleWords.containsAny("Head of E-Commerce", List.of("E-commerce"))).isTrue();
        assertThat(TitleWords.containsAny("Director, M&A and Strategy", List.of("M&A"))).isTrue();
        assertThat(TitleWords.containsAny("Head of Commerce", List.of("E-commerce"))).isFalse();
        assertThat(TitleWords.containsAny("Director", List.of())).isFalse();
        assertThat(TitleWords.containsAny(null, List.of("Director"))).isFalse();
    }

    @Test
    @DisplayName("a function is also found by the abbreviation its words spell out, as a word, never inside one")
    void aFunctionIsFoundByItsAbbreviation() {
        List<String> technology = List.of("Technology", "Information", "Digital");

        assertThat(TitleWords.containsFunction("Head of IT", technology)).isTrue();
        assertThat(TitleWords.containsFunction("Head of Information Technology", technology)).isTrue();
        assertThat(TitleWords.containsFunction("Head of Security at Kuwait Unit", technology)).isFalse();
        assertThat(TitleWords.containsFunction("Head of IT", List.of("Finance", "Financial"))).isFalse();
    }
}
