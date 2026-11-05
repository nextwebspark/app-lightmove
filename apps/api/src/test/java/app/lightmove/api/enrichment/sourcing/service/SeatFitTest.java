package app.lightmove.api.enrichment.sourcing.service;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.common.constant.Seniority;
import app.lightmove.api.enrichment.candidate.model.BrightDataPerson;
import app.lightmove.api.enrichment.candidate.model.BrightDataPerson.BrightDataExperience;
import app.lightmove.api.enrichment.sourcing.constant.SearchLevel;
import app.lightmove.api.enrichment.sourcing.model.SearchWords;
import app.lightmove.api.enrichment.sourcing.model.SourcingBrief;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Only someone whose current role names the function, at the lowest level searched or above, is filed — for
 * every kind of seat alike. The technology cases are titles a Head of IT run once filed and should not have.
 */
class SeatFitTest {

    private static final SearchWords TECHNOLOGY = words(SearchLevel.rangeFor(Seniority.C_SUITE),
            List.of("Technology", "Information Technology", "IT", "Information", "Digital"), List.of("CIO", "CTO"),
            List.of("Assistant", "Coordinator", "Support", "Analyst"));

    private static final SearchWords FINANCE = words(SearchLevel.rangeFor(Seniority.C_SUITE),
            List.of("Finance", "Financial"), List.of("CFO"), List.of("Assistant", "Accountant"));

    @Test
    @DisplayName("a Head of IT seat files technology heads and turns away the titles a search wrongly reached")
    void aTechnologySeat() {
        SeatFit seat = SeatFit.of(brief("Head of Information Technology", Seniority.C_SUITE), TECHNOLOGY);

        assertThat(List.of("Head of Information Technology", "Group Chief Information Officer",
                "Head – Information Technology । CIO", "Head of IT", "Director, Digital Transformation"))
                .allSatisfy(title -> assertThat(holds(seat, title)).as(title).isTrue());
        assertThat(List.of("Doctor of Pharmacy", "DHA pharmacist / Medical director/ senior pharmacist",
                "Division Commercial Manager - Surgical Infrastructure Solutions",
                "Head of Sales & Business development", "Information Technology Team Lead", "IT Support Coordinator",
                "Pharmacist", ""))
                .allSatisfy(title -> assertThat(holds(seat, title)).as(title).isFalse());
    }

    @Test
    @DisplayName("the lowest level searched is the floor: a C-suite seat files no manager, a junior seat files its specialists")
    void theLowestLevelSearched() {
        SeatFit cfo = SeatFit.of(brief("Group CFO", Seniority.C_SUITE), FINANCE);
        SearchWords financeBench = words(SearchLevel.rangeFor(Seniority.N_MINUS_2), List.of("Finance", "Financial"),
                List.of(), List.of("Assistant", "Intern"));
        SeatFit financeManager = SeatFit.of(brief("Finance Manager", Seniority.N_MINUS_2), financeBench);

        assertThat(holds(cfo, "Group Finance Director")).isTrue();
        assertThat(holds(cfo, "Chief Financial Officer")).isTrue();
        assertThat(holds(cfo, "Finance Manager")).isFalse();
        assertThat(holds(cfo, "Chief Accountant")).isFalse();
        assertThat(holds(cfo, "Chief Technology Officer")).isFalse();
        assertThat(holds(financeManager, "Finance Manager")).isTrue();
        assertThat(holds(financeManager, "Financial Analyst")).isTrue();
        assertThat(holds(financeManager, "Finance Intern")).isFalse();
    }

    @Test
    @DisplayName("other functions' seats are judged the same way, by the function's own words")
    void otherFunctions() {
        SearchWords people = words(SearchLevel.rangeFor(Seniority.N_MINUS_1),
                List.of("HR", "Human Resources", "People"), List.of("CHRO"), List.of("Assistant"));
        SeatFit hr = SeatFit.of(brief("Head of HR", Seniority.N_MINUS_1), people);
        SearchWords legal = words(SearchLevel.rangeFor(Seniority.C_SUITE), List.of("Legal", "Counsel", "Law"),
                List.of(), List.of("Paralegal"));
        SeatFit counsel = SeatFit.of(brief("General Counsel", Seniority.C_SUITE), legal);

        assertThat(holds(hr, "HR Director")).isTrue();
        assertThat(holds(hr, "Head of People & Culture")).isTrue();
        assertThat(holds(hr, "HR Manager")).isTrue();
        assertThat(holds(hr, "Talent Acquisition Specialist")).isFalse();
        assertThat(holds(counsel, "General Counsel")).isTrue();
        assertThat(holds(counsel, "Head of Legal")).isTrue();
        assertThat(holds(counsel, "Legal Counsel")).isFalse();
    }

    @Test
    @DisplayName("the general-management seat names no function, so only that seat itself is filed")
    void theTopSeat() {
        SearchWords topSeat = SearchWords.defaultFor("Chief Executive Officer", Seniority.C_SUITE);
        SeatFit ceo = SeatFit.of(brief("Chief Executive Officer", Seniority.C_SUITE), topSeat);

        assertThat(List.of("Chief Executive Officer", "Managing Director", "General Manager", "President & CEO",
                "Group CEO", "Group President"))
                .allSatisfy(title -> assertThat(holds(ceo, title)).as(title).isTrue());
        assertThat(List.of("Chief Financial Officer", "Finance Director", "Vice President Sales",
                "Executive Director", "Assistant to the CEO", "Chairman", "Executive Chairman",
                "President, Sales Division"))
                .allSatisfy(title -> assertThat(holds(ceo, title)).as(title).isFalse());
    }

    @Test
    @DisplayName("a title that does not fit says why: a leave-out word, a level below the search, or no function word")
    void aTitleThatDoesNotFitSaysWhy() {
        SeatFit cfo = SeatFit.of(brief("Group CFO", Seniority.C_SUITE), FINANCE);
        SeatFit ceo = SeatFit.of(brief("Chief Executive Officer", Seniority.C_SUITE),
                SearchWords.defaultFor("Chief Executive Officer", Seniority.C_SUITE));

        assertThat(cfo.whyNot("Executive Assistant to the CFO")).contains("LEAVE_OUT:Assistant");
        assertThat(cfo.whyNot("Finance Manager")).contains("BELOW_LEVEL");
        assertThat(cfo.whyNot("Chief Operating Officer")).contains("NO_FUNCTION_WORD");
        assertThat(ceo.whyNot("Chief Financial Officer")).contains("NOT_THE_TOP_SEAT");
        assertThat(cfo.whyNot("Group Finance Director")).isEmpty();
    }

    @Test
    @DisplayName("the job held now is judged, never a headline's self-description, which counts only when no job is on file")
    void theCurrentRoleIsJudged() {
        SeatFit seat = SeatFit.of(brief("Head of Information Technology", Seniority.C_SUITE), TECHNOLOGY);

        assertThat(holds(seat, person("Head of IT | ex-CIO", "Pharmacist"))).isFalse();
        assertThat(holds(seat, person("Doctor of Pharmacy", "Head of Information Technology"))).isTrue();
        assertThat(holds(seat, person("Doctor of Pharmacy", null))).isFalse();
        assertThat(holds(seat, person("Head of IT at a pharmacy chain", null))).isTrue();
    }

    private static boolean holds(SeatFit seat, String title) {
        return seat.whyNot(title).isEmpty();
    }

    private static boolean holds(SeatFit seat, BrightDataPerson person) {
        return seat.whyNot(CurrentRole.of(person)).isEmpty();
    }

    private static SearchWords words(List<SearchLevel> levels, List<String> terms, List<String> standalone,
                                     List<String> excluded) {
        return new SearchWords(levels, terms, standalone, List.of(), excluded, List.of(), null);
    }

    private static SourcingBrief brief(String roleTitle, Seniority seniority) {
        return new SourcingBrief(roleTitle, seniority, null, "Dubai", "United Arab Emirates", List.of(), null,
                List.of());
    }

    private static BrightDataPerson person(String headline, String currentTitle) {
        List<BrightDataExperience> experience = currentTitle == null ? List.of()
                : List.of(new BrightDataExperience("Employer", currentTitle, null, null, null, "2022", "Present", null,
                        null));
        return new BrightDataPerson("someone", "someone", "Someone", null, null, headline, null, null, "AE",
                "Employer", null, null, true, experience, List.of(), List.of(), List.of());
    }
}
