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
                .allSatisfy(title -> assertThat(seat.holds(title)).as(title).isTrue());
        assertThat(List.of("Doctor of Pharmacy", "DHA pharmacist / Medical director/ senior pharmacist",
                "Division Commercial Manager - Surgical Infrastructure Solutions",
                "Head of Sales & Business development", "Information Technology Team Lead", "IT Support Coordinator",
                "Pharmacist", ""))
                .allSatisfy(title -> assertThat(seat.holds(title)).as(title).isFalse());
    }

    @Test
    @DisplayName("the lowest level searched is the floor: a C-suite seat files no manager, a junior seat files its specialists")
    void theLowestLevelSearched() {
        SeatFit cfo = SeatFit.of(brief("Group CFO", Seniority.C_SUITE), FINANCE);
        SearchWords financeBench = words(SearchLevel.rangeFor(Seniority.N_MINUS_2), List.of("Finance", "Financial"),
                List.of(), List.of("Assistant", "Intern"));
        SeatFit financeManager = SeatFit.of(brief("Finance Manager", Seniority.N_MINUS_2), financeBench);

        assertThat(cfo.holds("Group Finance Director")).isTrue();
        assertThat(cfo.holds("Chief Financial Officer")).isTrue();
        assertThat(cfo.holds("Finance Manager")).isFalse();
        assertThat(cfo.holds("Chief Accountant")).isFalse();
        assertThat(cfo.holds("Chief Technology Officer")).isFalse();
        assertThat(financeManager.holds("Finance Manager")).isTrue();
        assertThat(financeManager.holds("Financial Analyst")).isTrue();
        assertThat(financeManager.holds("Finance Intern")).isFalse();
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

        assertThat(hr.holds("HR Director")).isTrue();
        assertThat(hr.holds("Head of People & Culture")).isTrue();
        assertThat(hr.holds("HR Manager")).isTrue();
        assertThat(hr.holds("Talent Acquisition Specialist")).isFalse();
        assertThat(counsel.holds("General Counsel")).isTrue();
        assertThat(counsel.holds("Head of Legal")).isTrue();
        assertThat(counsel.holds("Legal Counsel")).isFalse();
    }

    @Test
    @DisplayName("the general-management seat names no function, so only that seat itself is filed")
    void theTopSeat() {
        SearchWords topSeat = SearchWords.defaultFor("Chief Executive Officer", Seniority.C_SUITE);
        SeatFit ceo = SeatFit.of(brief("Chief Executive Officer", Seniority.C_SUITE), topSeat);

        assertThat(List.of("Chief Executive Officer", "Managing Director", "General Manager", "President & CEO",
                "Group CEO", "Group President"))
                .allSatisfy(title -> assertThat(ceo.holds(title)).as(title).isTrue());
        assertThat(List.of("Chief Financial Officer", "Finance Director", "Vice President Sales",
                "Executive Director", "Assistant to the CEO", "Chairman", "Executive Chairman",
                "President, Sales Division"))
                .allSatisfy(title -> assertThat(ceo.holds(title)).as(title).isFalse());
    }

    @Test
    @DisplayName("the job held now is judged, never a headline's self-description, which counts only when no job is on file")
    void theCurrentRoleIsJudged() {
        SeatFit seat = SeatFit.of(brief("Head of Information Technology", Seniority.C_SUITE), TECHNOLOGY);

        assertThat(seat.holds(person("Head of IT | ex-CIO", "Pharmacist"))).isFalse();
        assertThat(seat.holds(person("Doctor of Pharmacy", "Head of Information Technology"))).isTrue();
        assertThat(seat.holds(person("Doctor of Pharmacy", null))).isFalse();
        assertThat(seat.holds(person("Head of IT at a pharmacy chain", null))).isTrue();
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
