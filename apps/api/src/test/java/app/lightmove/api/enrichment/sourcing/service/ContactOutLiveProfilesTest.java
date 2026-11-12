package app.lightmove.api.enrichment.sourcing.service;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.RecordingContactOutPeopleIndex;
import app.lightmove.api.enrichment.sourcing.constant.SearchLevel;
import app.lightmove.api.enrichment.sourcing.model.SearchWords;
import app.lightmove.api.enrichment.sourcing.model.SearchedEmployer;
import app.lightmove.api.enrichment.sourcing.model.TitleTier;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** The search's words as ContactOut's filter: one Boolean title matched as whole words, and the free checks by name. */
class ContactOutLiveProfilesTest {

    private static final SearchWords FINANCE = new SearchWords(List.of(SearchLevel.VP_HEAD_DIRECTOR),
            List.of("Finance", "Real Estate"), List.of("CFO"), List.of(), List.of("Assistant"), List.of(), null);
    private static final SearchedEmployer HARBOUR = new SearchedEmployer("harbour-group", "harbour.example",
            "Harbour Group");

    private final RecordingContactOutPeopleIndex index = new RecordingContactOutPeopleIndex();
    private final ContactOutLiveProfiles live = new ContactOutLiveProfiles(index, JsonMapper.builder().build());

    @Test
    @DisplayName("a title is a level word and a function term, or a standalone title; phrases are quoted whole")
    void theTitleQuery() {
        assertThat(ContactOutLiveProfiles.wordsQuery(FINANCE)).isEqualTo(
                "((VP OR \"Vice President\" OR Head OR Director) AND (Finance OR \"Real Estate\")) OR (CFO)");
        assertThat(ContactOutLiveProfiles.wordsQuery(new SearchWords(List.of(), List.of("Finance"), List.of(),
                List.of(), List.of(), List.of(), null))).isEqualTo("(Finance)");
    }

    @Test
    @DisplayName("a wider tier asks for what the earlier tier did not find, so nobody is bought twice")
    void aWiderTierLeavesOutTheEarlier() {
        SearchWords wider = new SearchWords(List.of(SearchLevel.VP_HEAD_DIRECTOR, SearchLevel.MANAGER),
                List.of("Finance"), List.of(), List.of(), List.of(), List.of(), null);
        SearchWords first = wider.narrowedTo(SearchLevel.VP_HEAD_DIRECTOR);

        assertThat(ContactOutLiveProfiles.titleQuery(new TitleTier(wider, first))).isEqualTo(
                "((VP OR \"Vice President\" OR Head OR Director OR Manager OR Lead OR Principal) AND (Finance))"
                        + " NOT ((VP OR \"Vice President\" OR Head OR Director) AND (Finance))");
        assertThat(ContactOutLiveProfiles.titleQuery(TitleTier.of(first)))
                .isEqualTo("(VP OR \"Vice President\" OR Head OR Director) AND (Finance)");
    }

    @Test
    @DisplayName("a count asks for current titles at the company's domain, where it is worked, less the excluded words")
    void aCountsFilter() {
        live.count(HARBOUR, TitleTier.of(FINANCE), List.of("AE", "SA"));

        Map<String, Object> filter = index.counts().getLast();
        assertThat(filter.get("domain")).isEqualTo(List.of("harbour.example"));
        assertThat(filter).doesNotContainKeys("company", "name");
        assertThat(filter.get("job_title")).isEqualTo(List.of(ContactOutLiveProfiles.wordsQuery(FINANCE)));
        assertThat(filter.get("current_titles_only")).isEqualTo(true);
        assertThat(filter.get("exclude_job_titles")).isEqualTo(List.of("Assistant"));
        assertThat(filter.get("current_work_location")).isEqualTo(List.of("United Arab Emirates", "Saudi Arabia"));
    }

    @Test
    @DisplayName("without a domain the company is asked by name, and with neither it is not asked at all")
    void fallsBackToTheName() {
        live.count(new SearchedEmployer("harbour-group", null, " Harbour Group "), TitleTier.of(FINANCE), List.of());

        assertThat(index.counts().getLast().get("company")).isEqualTo(List.of("Harbour Group"));
        assertThat(index.counts().getLast()).doesNotContainKeys("domain", "current_work_location");
        assertThat(live.count(new SearchedEmployer("harbour-group", null, null), TitleTier.of(FINANCE), List.of()))
                .isZero();
        assertThat(index.counts()).hasSize(1);
    }

    @Test
    @DisplayName("a title is checked free by the person's name wherever they live, and a profile bought by name is one")
    void aCheckIsByName() {
        live.holdsTitle(HARBOUR, " Rania Haddad ", FINANCE);
        live.profileOf(HARBOUR, "Rania Haddad", FINANCE);

        assertThat(index.counts().getLast()).containsEntry("name", "Rania Haddad")
                .doesNotContainKey("current_work_location");
        assertThat(index.searches()).singleElement().satisfies(search -> {
            assertThat(search.body()).containsEntry("name", "Rania Haddad");
            assertThat(search.pageSize()).isEqualTo(1);
        });
        assertThat(live.holdsTitle(HARBOUR, " ", FINANCE)).isFalse();
        assertThat(index.counts()).hasSize(1);
    }
}
