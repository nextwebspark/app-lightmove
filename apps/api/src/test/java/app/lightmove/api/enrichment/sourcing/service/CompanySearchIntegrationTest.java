package app.lightmove.api.enrichment.sourcing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import app.lightmove.api.IntegrationTest;
import app.lightmove.api.common.constant.Seniority;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.enrichment.candidate.model.BrightDataPerson;
import app.lightmove.api.enrichment.candidate.model.BrightDataPerson.BrightDataExperience;
import app.lightmove.api.enrichment.sourcing.constant.FoundBy;
import app.lightmove.api.enrichment.sourcing.constant.SearchLevel;
import app.lightmove.api.enrichment.sourcing.constant.TitleCheck;
import app.lightmove.api.enrichment.sourcing.model.FoundPeople;
import app.lightmove.api.enrichment.sourcing.model.FoundPerson;
import app.lightmove.api.enrichment.sourcing.model.SearchWords;
import app.lightmove.api.enrichment.sourcing.model.SearchedEmployer;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * One company searched the way the measured strategy spends: live profiles bought in tiers up to the
 * company's budget, the profile index asked once, and each of its untitled people checked free — through
 * the real people cache, so what is bought once is never bought again.
 */
@IntegrationTest
class CompanySearchIntegrationTest {

    private static final SearchWords FINANCE = new SearchWords(
            List.of(SearchLevel.C_LEVEL, SearchLevel.SENIOR_VP, SearchLevel.VP_HEAD_DIRECTOR, SearchLevel.MANAGER),
            List.of("Finance", "Financial", "Treasury", "Tax", "Audit"), List.of("CFO"), List.of(),
            List.of("Assistant"), List.of(), "The group's finance chief.");
    private static final List<String> UAE = List.of("AE");
    private static final int PROFILES = 3;

    @Autowired private CompanySearch search;
    @Autowired private RecordingLiveProfiles live;
    @Autowired private RecordingProfileIndex index;
    @Autowired private LightMoveProperties properties;
    @Autowired private JdbcTemplate db;

    @BeforeEach
    void startClean() {
        live.clear();
        index.clear();
        db.update("DELETE FROM app_lm_vendor_person");
        db.update("DELETE FROM app_lm_vendor_people_search");
    }

    @Test
    @DisplayName("the seat's own levels and core terms are bought first, then the wider tier, never past three a company")
    void buysTheSeatsTierFirst() {
        Staff harbour = new Staff("harbour-group");
        live.answerWith(harbour.company(), List.of(
                harbour.titled("the-cfo", "Chief Financial Officer"),
                harbour.titled("head-treasury", "Head of Treasury"),
                harbour.titled("finance-manager", "Finance Manager"),
                harbour.titled("audit-head", "Head of Audit")));

        FoundPeople found = search.search(employer(harbour.company()), FINANCE, Seniority.C_SUITE, UAE, PROFILES);

        assertThat(live.searches()).extracting(RecordingLiveProfiles.Searched::size).containsExactly(2, 1);
        assertThat(live.searches().getFirst().tier().include().levels())
                .containsExactly(SearchLevel.C_LEVEL, SearchLevel.SENIOR_VP, SearchLevel.VP_HEAD_DIRECTOR);
        assertThat(live.searches().getFirst().tier().include().functionTerms()).doesNotContain("Audit");
        assertThat(slugs(found)).containsExactly("the-cfo", "head-treasury", "finance-manager");
        assertThat(found.people()).allSatisfy(person -> {
            assertThat(person.check()).isEqualTo(TitleCheck.LIVE_TITLE);
            assertThat(person.provider()).isEqualTo("contactout");
        });
        assertThat(found.credits()).isEqualTo(PROFILES);
        assertThat(found.matched()).isEqualTo(4);
    }

    @Test
    @DisplayName("a question asked again, or another whose fitting people are all on file, buys nobody")
    void nobodyIsBoughtTwice() {
        Staff cedar = new Staff("cedar-holdings");
        live.answerWith(cedar.company(), List.of(
                cedar.titled("the-cfo", "Chief Financial Officer"),
                cedar.titled("finance-manager", "Finance Manager")));
        FoundPeople first = search.search(employer(cedar.company()), FINANCE, Seniority.C_SUITE, UAE, PROFILES);
        assertThat(first.credits()).isEqualTo(2);

        FoundPeople again = search.search(employer(cedar.company()), FINANCE, Seniority.C_SUITE, UAE, PROFILES);
        SearchWords reworded = new SearchWords(FINANCE.levels(), FINANCE.functionTerms(), FINANCE.standaloneTitles(),
                List.of(), List.of("Assistant", "Intern"), List.of(), null);
        FoundPeople otherQuestion = search.search(employer(cedar.company()), reworded, Seniority.C_SUITE, UAE,
                PROFILES);

        assertThat(live.searches()).hasSize(2);
        assertThat(index.searches()).as("the profile index's answer is kept too").hasSize(2);
        assertThat(again.credits()).isZero();
        assertThat(again.cached()).isEqualTo(2);
        assertThat(otherQuestion.credits()).isZero();
        assertThat(slugs(otherQuestion)).containsExactly("the-cfo", "finance-manager");
    }

    @Test
    @DisplayName("the profile index's titles are taken as they are, and each untitled person is checked free by name")
    void untitledPeopleAreCheckedFree() {
        Staff dune = new Staff("dune-trading");
        live.answerWith(dune.company(), List.of(
                dune.titled("the-cfo", "Chief Financial Officer"),
                dune.titled("head-treasury", "Head of Treasury"),
                dune.titled("finance-director", "Finance Director"),
                dune.titled("treasury-director", "Rania Haddad", "Treasury Director")));
        index.answerWith(dune.company(), List.of(
                dune.untitled("treasury-director", "Rania Haddad"),
                dune.untitled("unknown-person", "Omar Khalil"),
                dune.titled("tax-head", "Group Head of Tax"),
                dune.untitled("the-cfo", "The CFO")));

        FoundPeople found = search.search(employer(dune.company()), FINANCE, Seniority.C_SUITE, UAE, PROFILES);

        assertThat(checksBySlug(found)).isEqualTo(Map.of(
                "the-cfo", TitleCheck.LIVE_TITLE,
                "head-treasury", TitleCheck.LIVE_TITLE,
                "finance-director", TitleCheck.LIVE_TITLE,
                "treasury-director", TitleCheck.CONFIRMED,
                "unknown-person", TitleCheck.UNCONFIRMED,
                "tax-head", TitleCheck.INDEX_TITLE));
        assertThat(live.titleChecks()).containsExactly("Rania Haddad", "Omar Khalil");
        assertThat(found.credits()).isEqualTo(3);
        assertThat(found.indexSearches()).isEqualTo(1);
        assertThat(found.titleChecks()).isEqualTo(2);
        assertThat(index.searches().getFirst().roleWords())
                .containsExactly("Finance", "Financial", "Treasury", "Tax", "Audit", "CFO");
    }

    @Test
    @DisplayName("someone both indexes found is one person, found by both, with the live record standing")
    void bothIndexesFindOnePerson() {
        Staff eastgate = new Staff("eastgate");
        live.answerWith(eastgate.company(), List.of(eastgate.titled("the-cfo", "Chief Financial Officer")));
        index.answerWith(eastgate.company(), List.of(eastgate.untitled("the-cfo", "The CFO"),
                eastgate.titled("other", "Finance Director")));

        FoundPeople found = search.search(employer(eastgate.company()), FINANCE, Seniority.C_SUITE, UAE, PROFILES);

        assertThat(found.people()).extracting(FoundPerson::slug, FoundPerson::foundBy, FoundPerson::check)
                .containsExactly(tuple("the-cfo", FoundBy.BOTH, TitleCheck.LIVE_TITLE),
                        tuple("other", FoundBy.INDEX, TitleCheck.INDEX_TITLE));
        assertThat(live.titleChecks()).isEmpty();
    }

    @Test
    @DisplayName("with the live index unavailable the profile index still answers, its untitled people unchecked")
    void theProfileIndexAnswersAlone() {
        Staff fernhill = new Staff("fernhill");
        live.offer(false);
        index.answerWith(fernhill.company(), List.of(fernhill.titled("director", "Finance Director"),
                fernhill.untitled("someone", "Someone")));

        FoundPeople found = search.search(employer(fernhill.company()), FINANCE, Seniority.C_SUITE, UAE, PROFILES);

        assertThat(checksBySlug(found)).isEqualTo(Map.of(
                "director", TitleCheck.INDEX_TITLE, "someone", TitleCheck.UNCHECKED));
        assertThat(live.titleChecks()).isEmpty();
        assertThat(found.credits()).isZero();
    }

    @Test
    @DisplayName("every untitled person on the profile index's page is checked free, up to the company's allowance")
    void titleChecksCoverThePage() {
        Staff granite = new Staff("granite-works");
        int page = properties.enrichment().sourcing().indexPeoplePerCompany();
        int checked = Math.min(page, properties.enrichment().sourcing().titleChecksPerCompany());
        index.answerWith(granite.company(), IntStream.range(0, page)
                .mapToObj(number -> granite.untitled("person-" + number, "Person " + number))
                .toList());

        FoundPeople found = search.search(employer(granite.company()), FINANCE, Seniority.C_SUITE, UAE, PROFILES);

        assertThat(live.titleChecks()).hasSize(checked);
        assertThat(found.people().subList(0, checked))
                .allSatisfy(person -> assertThat(person.check()).isEqualTo(TitleCheck.UNCONFIRMED));
        assertThat(found.people().subList(checked, page))
                .allSatisfy(person -> assertThat(person.check()).isEqualTo(TitleCheck.UNCHECKED));
    }

    private static SearchedEmployer employer(String slug) {
        return new SearchedEmployer(slug, null, slug);
    }

    private static List<String> slugs(FoundPeople found) {
        return found.people().stream().map(FoundPerson::slug).toList();
    }

    private static Map<String, TitleCheck> checksBySlug(FoundPeople found) {
        return found.people().stream().collect(Collectors.toMap(FoundPerson::slug, FoundPerson::check));
    }

    /** People at one company, as either index records them: current employer keyed on its slug. */
    private record Staff(String company) {

        BrightDataPerson titled(String slug, String title) {
            return titled(slug, slug, title);
        }

        BrightDataPerson titled(String slug, String name, String title) {
            return person(slug, name, title, List.of(new BrightDataExperience(company, title, null, null, null,
                    "Jan 2022", "Present", null, null)));
        }

        BrightDataPerson untitled(String slug, String name) {
            return person(slug, name, null, List.of());
        }

        private BrightDataPerson person(String slug, String name, String title, List<BrightDataExperience> roles) {
            return new BrightDataPerson(slug, slug, name, "https://www.linkedin.com/in/" + slug, null, title, "Dubai",
                    "Dubai", "AE", company, new BrightDataPerson.BrightDataCurrentCompany(company, company, null), null,
                    true, roles, List.of(), List.of(), List.of());
        }
    }
}
