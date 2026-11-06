package app.lightmove.api.enrichment.sourcing.service;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.common.constant.Seniority;
import app.lightmove.api.enrichment.candidate.model.BrightDataPerson;
import app.lightmove.api.enrichment.candidate.model.BrightDataPerson.BrightDataExperience;
import app.lightmove.api.enrichment.sourcing.model.SourcingBrief;
import app.lightmove.api.enrichment.sourcing.model.SourcingSpec;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Only someone whose current role names the brief's function, at the seat's level, is filed — for every
 * kind of seat alike. The technology cases are the titles a Head of IT run filed and should not have.
 */
class SeatFitTest {

    private static final SourcingSpec TECHNOLOGY = SourcingSpec.of(List.of("Head", "Director", "Chief", "CIO"),
            List.of("Technology", "Information", "Digital", "CIO"),
            List.of("Assistant", "Coordinator", "Support", "Analyst"), null);

    private static final SourcingSpec FINANCE = SourcingSpec.of(List.of("Chief", "CFO", "Head", "Director"),
            List.of("Finance", "Financial", "CFO"), List.of("Assistant", "Accountant"), null);

    @Test
    @DisplayName("a Head of IT seat files technology heads and turns away the titles its search wrongly reached")
    void aTechnologySeat() {
        SeatFit seat = SeatFit.of(brief("Head of Information Technology", Seniority.C_SUITE), TECHNOLOGY);

        assertThat(List.of("Head of Information Technology", "Group Chief Information Officer",
                "Head – Information Technology । CIO", "Head of IT", "Director, Digital Transformation"))
                .allSatisfy(title -> assertThat(seat.holds(title, TECHNOLOGY)).as(title).isTrue());
        assertThat(List.of("Doctor of Pharmacy", "DHA pharmacist / Medical director/ senior pharmacist",
                "Division Commercial Manager - Surgical Infrastructure Solutions",
                "Head of Sales & Business development", "Information Technology Team Lead", "IT Support Coordinator",
                "Pharmacist", ""))
                .allSatisfy(title -> assertThat(seat.holds(title, TECHNOLOGY)).as(title).isFalse());
    }

    @Test
    @DisplayName("a reworded round's own word proves the function only when no other function's titles share it")
    void aRewordedRoundsWords() {
        SeatFit technology = SeatFit.of(brief("Head of Information Technology", Seniority.C_SUITE), TECHNOLOGY);
        SourcingSpec infrastructureRound = SourcingSpec.of(List.of("Chief", "Director", "Head", "Manager"),
                List.of("Applications", "Infrastructure", "Network", "Systems"), List.of(), null);
        SeatFit finance = SeatFit.of(brief("Group CFO", Seniority.C_SUITE), FINANCE);
        SourcingSpec treasuryRound = SourcingSpec.of(List.of("Group", "Director"), List.of("Treasury"), List.of(), null);
        SourcingSpec developmentRound = SourcingSpec.of(List.of("Head", "Director"), List.of("Development"), List.of(),
                null);

        assertThat(technology.holds("Head of Infrastructure", infrastructureRound)).isFalse();
        assertThat(technology.holds("Head of IT Infrastructure", infrastructureRound)).isTrue();
        assertThat(finance.holds("Group Treasury Director", treasuryRound)).isTrue();
        assertThat(finance.holds("Business Development Director", developmentRound)).isFalse();
    }

    @Test
    @DisplayName("a C-suite or N-1 seat files a head or above, never a manager; an N-2 seat files a manager")
    void theSeatsLevel() {
        SeatFit cfo = SeatFit.of(brief("Group CFO", Seniority.C_SUITE), FINANCE);
        SeatFit financeManager = SeatFit.of(brief("Finance Manager", Seniority.N_MINUS_2),
                SourcingSpec.of(List.of("Manager", "Senior"), List.of("Finance", "Financial"), List.of(), null));

        assertThat(cfo.holds("Group Finance Director", FINANCE)).isTrue();
        assertThat(cfo.holds("Chief Financial Officer", FINANCE)).isTrue();
        assertThat(cfo.holds("Finance Manager", FINANCE)).isFalse();
        assertThat(cfo.holds("Chief Accountant", FINANCE)).isFalse();
        assertThat(cfo.holds("Chief Technology Officer", FINANCE)).isFalse();
        assertThat(financeManager.holds("Finance Manager", FINANCE)).isTrue();
        assertThat(financeManager.holds("Financial Analyst", FINANCE)).isFalse();
    }

    @Test
    @DisplayName("other functions' seats are judged the same way: HR by its abbreviation, legal by its counsel")
    void otherFunctions() {
        SourcingSpec people = SourcingSpec.of(List.of("Head", "Director", "Chief", "VP"),
                List.of("Human", "People", "CHRO", "Talent"), List.of("Assistant", "Specialist"), null);
        SeatFit hr = SeatFit.of(brief("Head of HR", Seniority.N_MINUS_1), people);
        SourcingSpec legal = SourcingSpec.of(List.of("Head", "General", "Chief", "Director"),
                List.of("Legal", "Counsel", "Law"), List.of("Paralegal"), null);
        SeatFit counsel = SeatFit.of(brief("General Counsel", Seniority.N_MINUS_1), legal);

        assertThat(hr.holds("HR Director", people)).isTrue();
        assertThat(hr.holds("Head of People & Culture", people)).isTrue();
        assertThat(hr.holds("HR Manager", people)).isFalse();
        assertThat(hr.holds("Talent Acquisition Specialist", people)).isFalse();
        assertThat(counsel.holds("General Counsel", legal)).isTrue();
        assertThat(counsel.holds("Legal Counsel", legal)).isFalse();
    }

    @Test
    @DisplayName("the general-management seat names no function, so only that seat itself is filed")
    void theTopSeat() {
        SourcingSpec topSeat = SourcingSpec.defaultFor("Chief Executive Officer", Seniority.C_SUITE);
        SeatFit ceo = SeatFit.of(brief("Chief Executive Officer", Seniority.C_SUITE), topSeat);

        assertThat(List.of("Chief Executive Officer", "Managing Director", "General Manager", "President & CEO",
                "Group CEO"))
                .allSatisfy(title -> assertThat(ceo.holds(title, topSeat)).as(title).isTrue());
        assertThat(List.of("Chief Financial Officer", "Finance Director", "Vice President Sales",
                "Executive Director", "Assistant to the CEO", "Chairman", "Executive Chairman",
                "President, Sales Division"))
                .allSatisfy(title -> assertThat(ceo.holds(title, topSeat)).as(title).isFalse());
        assertThat(ceo.holds("Group President", topSeat)).isTrue();
    }

    @Test
    @DisplayName("a brief naming no function at all is judged by level and exclusions alone, never read as the CEO seat")
    void aBriefNamingNoFunction() {
        SourcingSpec levelsOnly = SourcingSpec.of(List.of("Head", "Director"), List.of("Director", "Head"),
                List.of("Assistant"), null);
        SeatFit seat = SeatFit.of(brief("Head of AI", Seniority.N_MINUS_1), levelsOnly);

        assertThat(seat.holds("Head of Data Science", levelsOnly)).isTrue();
        assertThat(seat.holds("VP Engineering", levelsOnly)).isTrue();
        assertThat(seat.holds("Data Scientist", levelsOnly)).isFalse();
        assertThat(seat.holds("Assistant Director", levelsOnly)).isFalse();
        assertThat(SeatFit.of(brief(null, Seniority.C_SUITE), levelsOnly).holds("Head of Finance", levelsOnly))
                .isTrue();
    }

    @Test
    @DisplayName("a word other functions share still proves the function when it is the brief's own, never a reworded round's")
    void aSharedWordOfTheBriefsOwn() {
        SourcingSpec security = SourcingSpec.of(List.of("Chief", "Head", "Director", "CISO"),
                List.of("Security", "Cybersecurity", "CISO"), List.of("Analyst"), null);
        SeatFit ciso = SeatFit.of(brief("Chief Information Security Officer", Seniority.C_SUITE), security);
        SeatFit technology = SeatFit.of(brief("Head of Information Technology", Seniority.C_SUITE), TECHNOLOGY);
        SourcingSpec securityRound = SourcingSpec.of(List.of("Head"), List.of("Security", "Services"), List.of(),
                null);

        assertThat(ciso.holds("Head of Cyber Security", security)).isTrue();
        assertThat(technology.holds("Head of Security", securityRound)).isFalse();
        assertThat(technology.holds("Head of IT Services", securityRound)).isTrue();
    }

    @Test
    @DisplayName("a function list holding only level words falls back to the role title's own words")
    void aLevelOnlyFunctionListFallsBack() {
        SourcingSpec levelsOnly = SourcingSpec.of(List.of("Head", "Group"), List.of("Director", "Head"), List.of(),
                null);
        SeatFit treasury = SeatFit.of(brief("Head of Treasury", Seniority.N_MINUS_1), levelsOnly);

        assertThat(treasury.holds("Group Treasury Director", levelsOnly)).isTrue();
        assertThat(treasury.holds("Medical Director", levelsOnly)).isFalse();
    }

    @Test
    @DisplayName("the job held now is judged, never a headline's self-description, which counts only when no job is on file")
    void theCurrentRoleIsJudged() {
        SeatFit seat = SeatFit.of(brief("Head of Information Technology", Seniority.C_SUITE), TECHNOLOGY);

        assertThat(seat.holds(person("Head of IT | ex-CIO", "Pharmacist"), TECHNOLOGY)).isFalse();
        assertThat(seat.holds(person("Doctor of Pharmacy", "Head of Information Technology"), TECHNOLOGY)).isTrue();
        assertThat(seat.holds(person("Doctor of Pharmacy", null), TECHNOLOGY)).isFalse();
        assertThat(seat.holds(person("Head of IT at a pharmacy chain", null), TECHNOLOGY)).isTrue();
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
