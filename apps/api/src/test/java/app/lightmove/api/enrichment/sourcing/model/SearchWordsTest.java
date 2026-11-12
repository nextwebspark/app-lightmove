package app.lightmove.api.enrichment.sourcing.model;

import static app.lightmove.api.enrichment.sourcing.constant.SearchLevel.C_LEVEL;
import static app.lightmove.api.enrichment.sourcing.constant.SearchLevel.MANAGER;
import static app.lightmove.api.enrichment.sourcing.constant.SearchLevel.SENIOR_VP;
import static app.lightmove.api.enrichment.sourcing.constant.SearchLevel.VP_HEAD_DIRECTOR;
import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.common.constant.Seniority;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/**
 * The words reach a vendor's Boolean query and a stored run, so they are cleaned whoever wrote them, and
 * a search the model could not draft still has words of its own.
 */
class SearchWordsTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Test
    @DisplayName("brackets, quotes and operators never reach a query; repeats, level-only terms and long phrases are dropped")
    void wordsAreCleanedWhoeverWroteThem() {
        SearchWords words = new SearchWords(List.of(VP_HEAD_DIRECTOR, C_LEVEL, C_LEVEL),
                List.of("Finance", "finance", "\"Treasury\"", "(Tax)", "OR", "Head", "Senior Director", "Risk OR Audit",
                        "Mergers and Acquisitions", "Group Shared Service Centre"),
                List.of("CFO"), null, List.of("Assistant", "Finance", "head"), List.of("Treasury", "Audit", "Director"),
                "  Group finance chief. ");

        assertThat(words.levels()).containsExactly(C_LEVEL, VP_HEAD_DIRECTOR);
        assertThat(words.functionTerms()).containsExactly("Finance", "Treasury", "Tax", "Mergers and Acquisitions");
        assertThat(words.localLevelWords()).isEmpty();
        assertThat(words.excludedWords()).as("never a word the search itself looks for").containsExactly("Assistant");
        assertThat(words.suggestions()).as("never a term already searched, nor a level").containsExactly("Audit");
        assertThat(words.roleSummary()).isEqualTo("Group finance chief.");
    }

    @Test
    @DisplayName("each list is held to its cap, the earliest kept, and nothing excluded or suggested is already searched")
    void listsAreCapped() {
        List<String> many = IntStream.range(0, 20).mapToObj(index -> "Term" + index).toList();

        SearchWords words = new SearchWords(List.of(), many, many, many, many, many, null);

        assertThat(words.functionTerms()).hasSize(SearchWords.MAX_FUNCTION_TERMS).startsWith("Term0");
        assertThat(words.standaloneTitles()).hasSize(SearchWords.MAX_STANDALONE_TITLES);
        assertThat(words.localLevelWords()).hasSize(SearchWords.MAX_LOCAL_LEVEL_WORDS);
        assertThat(words.excludedWords()).containsExactly("Term12", "Term13", "Term14", "Term15");
        assertThat(words.suggestions()).hasSize(SearchWords.MAX_SUGGESTIONS).startsWith("Term12");
    }

    @Test
    @DisplayName("the first tier keeps the levels from its floor up and the core terms; the rest is the wider tier's")
    void narrowingKeepsTheSeatAndTheCore() {
        SearchWords words = new SearchWords(List.of(C_LEVEL, SENIOR_VP, VP_HEAD_DIRECTOR, MANAGER),
                List.of("Finance", "Financial", "Treasury", "Tax", "Audit", "Accounting"), List.of("CFO"), List.of(),
                List.of("Assistant"), List.of("Risk"), "The group's finance chief.");

        SearchWords first = words.narrowedTo(VP_HEAD_DIRECTOR);

        assertThat(first.levels()).containsExactly(C_LEVEL, SENIOR_VP, VP_HEAD_DIRECTOR);
        assertThat(first.functionTerms()).containsExactly("Finance", "Financial", "Treasury", "Tax");
        assertThat(first.standaloneTitles()).containsExactly("CFO");
        assertThat(first.excludedWords()).containsExactly("Assistant");
        assertThat(first.suggestions()).isEmpty();
        assertThat(words.lowestLevel(Seniority.C_SUITE)).isEqualTo(MANAGER);
    }

    @Test
    @DisplayName("the same question in another order or case is one key; another exclusion or level is not")
    void keysAreCanonical() {
        SearchWords words = new SearchWords(List.of(C_LEVEL, VP_HEAD_DIRECTOR), List.of("Finance", "Treasury"),
                List.of("CFO"), List.of(), List.of("Assistant"), List.of(), null);

        assertThat(new SearchWords(List.of(VP_HEAD_DIRECTOR, C_LEVEL), List.of("treasury", "FINANCE"), List.of("cfo"),
                List.of(), List.of("assistant"), List.of("Risk"), "another summary").key()).isEqualTo(words.key());
        assertThat(new SearchWords(List.of(C_LEVEL, VP_HEAD_DIRECTOR), List.of("Finance", "Treasury"), List.of("CFO"),
                List.of(), List.of(), List.of(), null).key()).isNotEqualTo(words.key());
        assertThat(new SearchWords(List.of(C_LEVEL), List.of("Finance", "Treasury"), List.of("CFO"), List.of(),
                List.of("Assistant"), List.of(), null).key()).isNotEqualTo(words.key());
    }

    @Test
    @DisplayName("a match over a whole profile keys on the function's own words, never a level word inside a title")
    void roleWordsLeaveOutLevels() {
        SearchWords realEstate = new SearchWords(List.of(C_LEVEL), List.of("Real Estate", "Business Development",
                "IT"), List.of("Managing Director"), List.of(), List.of(), List.of(), null);

        assertThat(realEstate.roleWords()).containsExactly("Real", "Estate", "Development", "IT");
        assertThat(SearchWords.defaultFor("Chief Executive Officer", Seniority.C_SUITE).roleWords())
                .containsExactly("CEO");
    }

    @Test
    @DisplayName("with no draft, a chief's title gives its abbreviation and function, spelt every usual way")
    void theDefaultReadsTheRoleTitle() {
        SearchWords groupCfo = SearchWords.defaultFor("Group CFO", Seniority.C_SUITE);
        SearchWords spelledOut = SearchWords.defaultFor("Chief Financial Officer", Seniority.C_SUITE);
        SearchWords hr = SearchWords.defaultFor("Head of HR", Seniority.N_MINUS_1);
        SearchWords people = SearchWords.defaultFor("Chief People Officer", Seniority.C_SUITE);

        assertThat(groupCfo.levels()).containsExactly(C_LEVEL, SENIOR_VP, VP_HEAD_DIRECTOR);
        assertThat(groupCfo.standaloneTitles()).containsExactly("CFO");
        assertThat(groupCfo.functionTerms()).containsExactly("Finance", "Financial");
        assertThat(groupCfo.excludedWords()).containsExactly("Assistant", "Secretary", "Coordinator", "Intern");
        assertThat(spelledOut.standaloneTitles()).containsExactly("CFO");
        assertThat(spelledOut.functionTerms()).containsExactly("Financial", "Finance");
        assertThat(hr.levels()).containsExactly(C_LEVEL, SENIOR_VP, VP_HEAD_DIRECTOR, MANAGER);
        assertThat(hr.functionTerms()).containsExactly("HR", "Human Resources", "Human Resource", "People");
        assertThat(people.standaloneTitles()).containsExactly("CPO");
        assertThat(people.functionTerms()).as("CPO also stands for product and procurement, so only the title's own")
                .containsExactly("People", "HR", "Human Resources", "Human Resource");
    }

    @Test
    @DisplayName("with no draft, the top seat is searched by its own titles, a division's head by the division")
    void theDefaultForTheTopSeatAndADivision() {
        SearchWords ceo = SearchWords.defaultFor("Chief Executive Officer", Seniority.C_SUITE);
        SearchWords realEstate = SearchWords.defaultFor("General Manager, Real Estate", Seniority.C_SUITE);

        assertThat(ceo.levels()).containsExactly(C_LEVEL);
        assertThat(ceo.functionTerms()).isEmpty();
        assertThat(ceo.standaloneTitles()).containsExactly("CEO", "Chief Executive", "Managing Director",
                "General Manager");
        assertThat(ceo.isSearchable()).isTrue();
        assertThat(realEstate.functionTerms()).containsExactly("Real Estate");
        assertThat(realEstate.standaloneTitles()).isEmpty();
        assertThat(SearchWords.defaultFor(null, null).isSearchable()).isFalse();
    }

    @Test
    @DisplayName("a run recorded before the words changed shape reads back, and a stored run keeps only the words")
    void storedRunsReadBack() {
        SearchWords recorded = JSON.readValue("""
                {"seniorityWords":["Chief","Head"],"functionWords":["Finance","Financial"],
                 "excludedWords":["Assistant"],"roleSummary":"The group's finance chief."}""", SearchWords.class);

        assertThat(recorded.levelWords()).containsExactly("Chief", "Head");
        assertThat(recorded.functionTerms()).containsExactly("Finance", "Financial");
        assertThat(recorded.excludedWords()).containsExactly("Assistant");
        String stored = JSON.writeValueAsString(recorded);
        assertThat(stored).doesNotContain("searchable");
        assertThat(JSON.readValue(stored, SearchWords.class)).isEqualTo(recorded);
    }
}
