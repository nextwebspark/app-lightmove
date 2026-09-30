package app.lightmove.api.enrichment.sourcing.model;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.common.constant.Seniority;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Whatever the model answers, what reaches the vendor is single words, four at most, none twice, and
 * no excluded word that would also drop a searched or senior title.
 */
class SourcingSpecTest {

    @Test
    @DisplayName("a phrase is split into its words, punctuation stripped, repeats and short tokens dropped")
    void cleansTheModelsWords() {
        SourcingSpec spec = SourcingSpec.of(List.of("Chief Financial", " head of ", "CFO.", "chief", "Director", "VP"),
                Arrays.asList("Finance", null, "Financial,", "Treasury", "Tax", "Accounting"), List.of(), "  A CFO.  ");

        assertThat(spec.seniorityWords()).containsExactly("Chief", "Financial", "head", "CFO");
        assertThat(spec.functionWords()).containsExactly("Finance", "Financial", "Treasury", "Tax");
        assertThat(spec.roleSummary()).isEqualTo("A CFO.");
    }

    @Test
    @DisplayName("the fallback searches the seniority's usual words and the title's own distinctive ones")
    void fallsBackToTheTitle() {
        SourcingSpec spec = SourcingSpec.defaultFor("Group Head of Treasury & Investor Relations", Seniority.N_MINUS_1);

        assertThat(spec.seniorityWords()).containsExactly("Chief", "Head", "Director", "VP");
        assertThat(spec.functionWords()).containsExactly("Treasury", "Investor", "Relations");
    }

    @Test
    @DisplayName("a general-management title falls back to the top-seat pairing, never a seniority group alone")
    void aGeneralManagerSearchesTheTopSeat() {
        SourcingSpec spec = SourcingSpec.defaultFor("Managing Director", Seniority.C_SUITE);

        assertThat(spec.seniorityWords()).containsExactly("CEO", "Chief", "Managing", "President");
        assertThat(spec.functionWords()).containsExactly("CEO", "Executive", "Director");
        assertThat(spec.excludedWords()).containsExactly("Assistant", "Vice", "Secretary");
    }

    @Test
    @DisplayName("an excluded word hiding inside a searched or senior title word is dropped; the rest are kept")
    void dropsExclusionsThatWouldHideTheRole() {
        SourcingSpec spec = SourcingSpec.of(List.of("Chief", "Head"), List.of("Finance", "CFO"),
                List.of("Office", "Intern", "Fin", "Assistant", "Accountant", "Secretary", "Coordinator"), null);

        assertThat(spec.excludedWords()).containsExactly("Assistant", "Accountant", "Secretary", "Coordinator");
    }

    @Test
    @DisplayName("an abbreviation hiding inside ordinary words is never searched; a function's is spelled out")
    void dropsNoiseWords() {
        SourcingSpec spec = SourcingSpec.of(List.of("Head", "MD"), List.of("Technology", "IT", "Digital"),
                List.of(), null);
        SourcingSpec people = SourcingSpec.of(List.of("Head"), List.of("HR", "Human"), List.of(), null);

        assertThat(spec.seniorityWords()).containsExactly("Head");
        assertThat(spec.functionWords()).containsExactly("Technology", "CIO", "CTO", "Digital");
        assertThat(people.functionWords()).containsExactly("Human", "People", "CHRO");
    }

    @Test
    @DisplayName("a title's abbreviation falls back to its spellings, never to the top-seat pairing")
    void theFallbackSpellsAnAbbreviationOut() {
        SourcingSpec spec = SourcingSpec.defaultFor("Head of HR", Seniority.N_MINUS_2);

        assertThat(spec.seniorityWords()).containsExactly("Manager", "Head", "Senior", "Principal");
        assertThat(spec.functionWords()).containsExactly("Human", "People", "CHRO");
    }

    @Test
    @DisplayName("a spec stored before exclusions existed reads back with none")
    void anOlderSpecHasNoExclusions() {
        assertThat(new SourcingSpec(List.of("Chief"), List.of("Finance"), null, null).excludedWords()).isEmpty();
    }

    @Test
    @DisplayName("two specs asking the same words in another order or case share a key; other words do not")
    void wordKeys() {
        SourcingSpec spec = SourcingSpec.of(List.of("Chief", "Head"), List.of("Finance"), List.of("Assistant"), "A CFO.");

        assertThat(SourcingSpec.of(List.of("head", "CHIEF"), List.of("finance"), List.of("assistant"), null).wordKey())
                .isEqualTo(spec.wordKey());
        assertThat(SourcingSpec.of(List.of("Chief", "Head"), List.of("Treasury"), List.of("Assistant"), null).wordKey())
                .isNotEqualTo(spec.wordKey());
    }
}
