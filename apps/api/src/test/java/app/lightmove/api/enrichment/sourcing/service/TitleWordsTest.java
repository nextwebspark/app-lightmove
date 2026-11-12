package app.lightmove.api.enrichment.sourcing.service;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.enrichment.sourcing.constant.SearchLevel;
import app.lightmove.api.enrichment.sourcing.model.SearchWords;
import app.lightmove.api.enrichment.sourcing.model.TitleTier;
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
    @DisplayName("a title is found by a level word and a function term, or a standalone title, and lost to an excluded word")
    void aTitleHoldsTheWords() {
        SearchWords finance = new SearchWords(List.of(SearchLevel.VP_HEAD_DIRECTOR), List.of("Finance", "Treasury"),
                List.of("CFO"), List.of(), List.of("Assistant"), List.of(), null);

        assertThat(List.of("Head of Finance", "Treasury Director", "Group CFO", "VP, Finance"))
                .allSatisfy(title -> assertThat(TitleWords.holds(title, finance)).as(title).isTrue());
        assertThat(List.of("Finance Manager", "Head of Sales", "Assistant to the CFO", "Finance Assistant Director"))
                .allSatisfy(title -> assertThat(TitleWords.holds(title, finance)).as(title).isFalse());
        assertThat(TitleWords.holds(null, finance)).isFalse();
    }

    @Test
    @DisplayName("the market's own level words count as levels, and words naming no level take any title of the function")
    void localAndAbsentLevels() {
        SearchWords turkish = new SearchWords(List.of(), List.of("Planlama"), List.of(), List.of("Direktörü"),
                List.of(), List.of(), null);
        SearchWords anyLevel = new SearchWords(List.of(), List.of("Finance"), List.of(), List.of(), List.of(),
                List.of(), null);

        assertThat(TitleWords.holds("Planlama Direktörü", turkish)).isTrue();
        assertThat(TitleWords.holds("Planlama Uzmanı", turkish)).isFalse();
        assertThat(TitleWords.holds("Finance Analyst", anyLevel)).isTrue();
    }

    @Test
    @DisplayName("a wider tier holds only the titles its earlier tier did not, so nobody is bought twice")
    void aWiderTierLeavesOutTheEarlier() {
        SearchWords wider = new SearchWords(List.of(SearchLevel.VP_HEAD_DIRECTOR, SearchLevel.MANAGER),
                List.of("Finance"), List.of(), List.of(), List.of(), List.of(), null);
        SearchWords first = wider.narrowedTo(SearchLevel.VP_HEAD_DIRECTOR);
        TitleTier secondTier = new TitleTier(wider, first);

        assertThat(TitleWords.holds("Finance Manager", secondTier)).isTrue();
        assertThat(TitleWords.holds("Head of Finance", secondTier)).isFalse();
        assertThat(TitleWords.holds("Head of Finance", TitleTier.of(first))).isTrue();
    }
}
