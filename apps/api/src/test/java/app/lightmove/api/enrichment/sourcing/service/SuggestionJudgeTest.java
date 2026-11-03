package app.lightmove.api.enrichment.sourcing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import app.lightmove.api.candidate.constant.EnrichmentVendor;
import app.lightmove.api.common.constant.Seniority;
import app.lightmove.api.enrichment.candidate.model.BrightDataPerson;
import app.lightmove.api.enrichment.candidate.model.BrightDataPerson.BrightDataExperience;
import app.lightmove.api.enrichment.sourcing.constant.FoundBy;
import app.lightmove.api.enrichment.sourcing.constant.SearchLevel;
import app.lightmove.api.enrichment.sourcing.constant.SuggestionBand;
import app.lightmove.api.enrichment.sourcing.constant.SuggestionVerdict;
import app.lightmove.api.enrichment.sourcing.constant.TitleCheck;
import app.lightmove.api.enrichment.sourcing.model.FoundPerson;
import app.lightmove.api.enrichment.sourcing.model.SearchWords;
import app.lightmove.api.enrichment.sourcing.model.SourcingBrief;
import app.lightmove.api.enrichment.sourcing.model.Suggestion;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The panel's order and its reasons: fixed points per the design's table, never a model. A Group CFO search at
 * Dubai, judged in November 2026.
 */
class SuggestionJudgeTest {

    private static final SourcingBrief BRIEF = new SourcingBrief("Group CFO", Seniority.C_SUITE, null, "Dubai",
            "United Arab Emirates", List.of(), null, List.of());
    private static final SearchWords FINANCE = new SearchWords(
            List.of(SearchLevel.C_LEVEL, SearchLevel.SENIOR_VP, SearchLevel.VP_HEAD_DIRECTOR),
            List.of("Finance", "Financial", "Treasury"), List.of("CFO"), List.of(), List.of("Assistant"), List.of(),
            null);
    private static final List<String> SEARCHED = List.of("AE", "SA", "QA", "KW", "BH", "OM");
    private static final SuggestionJudge JUDGE = SuggestionJudge.of(BRIEF, FINANCE, SEARCHED, YearMonth.of(2026, 11));

    @Test
    @DisplayName("those who fit come first, best score first; then those with no title; then those who don't fit, each with why")
    void ordersAndExplains() {
        List<Suggestion> judged = JUDGE.judge(List.of(
                live("assistant", "Executive Assistant to the CFO", "AE", "Jan 2020"),
                found(untitled("confirmed", "AE"), TitleCheck.CONFIRMED, FoundBy.INDEX),
                found(untitled("unconfirmed", "AE"), TitleCheck.UNCONFIRMED, FoundBy.INDEX),
                found(titled("treasury", "Head of Treasury", "SA", "Jun 2026"), TitleCheck.LIVE_TITLE, FoundBy.BOTH),
                live("former-cfo", "Chief Strategy Officer", "AE", "Jan 2024"),
                live("group-cfo", "Group CFO", "AE", "Jan 2022")));

        assertThat(judged).extracting(Suggestion::linkedinSlug, Suggestion::verdict, Suggestion::score,
                        Suggestion::rank)
                .containsExactly(
                        tuple("group-cfo", SuggestionVerdict.FITS, 91, 1),
                        tuple("treasury", SuggestionVerdict.FITS, 55, 2),
                        tuple("confirmed", SuggestionVerdict.FITS, 40, 3),
                        tuple("unconfirmed", SuggestionVerdict.NO_TITLE, null, 4),
                        tuple("assistant", SuggestionVerdict.NOT_FIT, null, 5),
                        tuple("former-cfo", SuggestionVerdict.NOT_FIT, null, 6));
        assertThat(judged.get(0).reasons()).containsExactly("ROLE_WORD:CFO", "LEVEL:TOP", "LIVES_IN_ROLE_COUNTRY:AE");
        assertThat(judged.get(1).reasons()).containsExactly("SEARCH_WORD:Treasury", "LEVEL:HEAD", "LIVES_IN_REGION:SA",
                "FOUND_BY_BOTH", "NEW_IN_ROLE:5");
        assertThat(judged.get(2).reasons()).containsExactly("TITLE_CONFIRMED", "LIVES_IN_ROLE_COUNTRY:AE");
        assertThat(judged.get(4).reasons()).containsExactly("LEAVE_OUT:Assistant");
        assertThat(judged.get(5).reasons()).containsExactly("NO_FUNCTION_WORD");
    }

    @Test
    @DisplayName("a score of 75 or more is Strong, 55 to 74 Good, below that Possible")
    void bands() {
        assertThat(List.of(91, 75, 74, 55, 54, 40)).extracting(SuggestionBand::of).containsExactly(
                SuggestionBand.STRONG, SuggestionBand.STRONG, SuggestionBand.GOOD, SuggestionBand.GOOD,
                SuggestionBand.POSSIBLE, SuggestionBand.POSSIBLE);
    }

    @Test
    @DisplayName("someone living outside the searched countries ranks lower, and is never dropped")
    void elsewhereRanksLower() {
        List<Suggestion> judged = JUDGE.judge(List.of(live("london", "Group CFO", "GB", "Jan 2022"),
                live("dubai", "Group CFO", "AE", "Jan 2022")));

        assertThat(judged).extracting(Suggestion::linkedinSlug).containsExactly("dubai", "london");
        assertThat(judged.get(1).reasons()).contains("LIVES_ELSEWHERE:GB");
    }

    @Test
    @DisplayName("a role's start reads in each spelling the indexes send, and an unreadable one is no tenure")
    void readsWhenARoleBegan() {
        assertThat(SuggestionJudge.monthOf("Mar 2025")).contains(YearMonth.of(2025, 3));
        assertThat(SuggestionJudge.monthOf("March 2021")).contains(YearMonth.of(2021, 3));
        assertThat(SuggestionJudge.monthOf("2022-03")).contains(YearMonth.of(2022, 3));
        assertThat(SuggestionJudge.monthOf("2020")).contains(YearMonth.of(2020, 1));
        assertThat(SuggestionJudge.monthOf("soon")).isEmpty();
    }

    private static FoundPerson live(String slug, String title, String country, String since) {
        return found(titled(slug, title, country, since), TitleCheck.LIVE_TITLE, FoundBy.LIVE);
    }

    private static FoundPerson found(BrightDataPerson person, TitleCheck check, FoundBy foundBy) {
        return new FoundPerson(person, "contactout", EnrichmentVendor.CONTACTOUT, check, foundBy);
    }

    private static BrightDataPerson titled(String slug, String title, String country, String since) {
        return new BrightDataPerson(slug, slug, slug, null, null, title, null, null, country, "Harbour Group", null,
                null, true, List.of(new BrightDataExperience("Harbour Group", title, null, null, null, since,
                        "Present", null, null)), List.of(), List.of(), List.of());
    }

    private static BrightDataPerson untitled(String slug, String country) {
        return new BrightDataPerson(slug, slug, slug, null, null, null, null, null, country, "Harbour Group", null,
                null, true, List.of(), List.of(), List.of(), List.of());
    }
}
