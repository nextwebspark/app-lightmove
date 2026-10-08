package app.lightmove.api.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.ApolloUniverse;
import app.lightmove.api.IntegrationTest;
import app.lightmove.api.strategy.model.CompanyRow;
import app.lightmove.api.strategy.model.ScoredCompanyRow;
import app.lightmove.api.strategy.model.SimilarityScope;
import app.lightmove.api.strategy.service.ApolloCompanyQueryService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** Finding a company by part of its name, and companies by the niche they share with it, in real SQL. */
@IntegrationTest
class CompanySimilarityIntegrationTest {

    private static final String UAE = "United Arab Emirates";
    private static final List<String> SEDDIQI_NICHE = List.of("luxury watches", "watch retail", "jewellery",
            "heritage brand", "retail");

    /** Enough of the market that "watch retail", on five companies, is still under 3% of it. */
    private static final int GROCERS = 200;

    @Autowired JdbcTemplate db;

    @Autowired ApolloCompanyQueryService companies;

    @BeforeEach
    void aWatchMarket() {
        ApolloUniverse universe = new ApolloUniverse(db);
        universe.reset();
        universe.company("seddiqi", "Ahmed Seddiqi & Sons").industry("luxury goods & jewelry").country(UAE)
                .employees(950).keywords(SEDDIQI_NICHE.toArray(String[]::new)).insert();
        universe.company("seddiqi-holding", "Seddiqi Holding").industry("retail").country(UAE)
                .employees(1400).keywords("watches", "real estate", "retail").insert();
        universe.company("rivoli", "Rivoli Group").industry("retail").country(UAE).employees(2000)
                .keywords("luxury watches", "watch retail", "heritage brand", "retail").insert();
        universe.company("damas", "Damas Jewellery").industry("luxury goods & jewelry").country(UAE).employees(1900)
                .keywords("jewellery", "watch retail", "retail").insert();
        universe.company("lulu", "Lulu Hypermarket").industry("retail").country(UAE).employees(50_000)
                .keywords("retail", "groceries").insert();
        universe.company("lama", "Lama Watches").industry("retail").country("Saudi Arabia").employees(300)
                .keywords("luxury watches", "watch retail").insert();
        universe.company("timehouse", "Time House").industry("retail").country(UAE).employees(240)
                .keywords("watch retail").insert();
        for (int filler = 0; filler < GROCERS; filler++) {
            universe.company("filler-" + filler, "Grocer " + filler).industry("retail").country(UAE)
                    .employees(100).keywords("retail", "groceries").insert();
        }
        universe.refreshKeywordVocabulary();
    }

    @Test
    @DisplayName("every word of a name is enough to find a company under a longer one, biggest first")
    void findsANameUnderItsLongerForms() {
        List<CompanyRow> found = companies.namedLike(List.of("seddiqi"), UAE, 5);

        assertThat(found).extracting(CompanyRow::companyName)
                .containsExactly("Seddiqi Holding", "Ahmed Seddiqi & Sons");
    }

    @Test
    @DisplayName("peers rank by the rare niche they share, and a keyword everyone uses counts for nothing")
    void ranksPeersByTheRareNicheTheyShare() {
        List<ScoredCompanyRow> similar = companies.similarTo(new SimilarityScope(SEDDIQI_NICHE, List.of(UAE),
                List.of(), null, null, List.of("seddiqi"), 2, 10));

        assertThat(similar).extracting(scored -> scored.row().companyName())
                .containsExactly("Rivoli Group", "Damas Jewellery");
        assertThat(similar.getFirst().sharedKeywords()).doesNotContain("retail")
                .contains("luxury watches", "watch retail", "heritage brand");
    }

    @Test
    @DisplayName("a niche narrows by country, industry and headcount, and one shared keyword is enough when asked")
    void narrowsByCountryIndustryAndSize() {
        List<ScoredCompanyRow> inSaudi = companies.similarTo(new SimilarityScope(SEDDIQI_NICHE,
                List.of("Saudi Arabia"), List.of(), null, null, List.of(), 2, 10));
        List<ScoredCompanyRow> jewellers = companies.similarTo(new SimilarityScope(SEDDIQI_NICHE, List.of(UAE),
                List.of("luxury goods & jewelry"), null, null, List.of("seddiqi"), 1, 10));
        List<ScoredCompanyRow> small = companies.similarTo(new SimilarityScope(SEDDIQI_NICHE, List.of(UAE),
                List.of(), 100L, 500L, List.of(), 1, 10));

        assertThat(inSaudi).extracting(scored -> scored.row().companyName()).containsExactly("Lama Watches");
        assertThat(jewellers).extracting(scored -> scored.row().companyName()).containsExactly("Damas Jewellery");
        assertThat(small).extracting(scored -> scored.row().companyName()).containsExactly("Time House");
    }

    @Test
    @DisplayName("of a company's keywords, its niche is the ones neither unique to it nor common to all")
    void readsTheNicheOffTheKeywords() {
        assertThat(companies.distinctiveKeywords(SEDDIQI_NICHE, 10))
                .containsExactlyInAnyOrder("luxury watches", "watch retail", "jewellery", "heritage brand");
    }
}
