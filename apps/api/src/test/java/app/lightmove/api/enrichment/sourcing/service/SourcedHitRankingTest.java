package app.lightmove.api.enrichment.sourcing.service;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.common.constant.Seniority;
import app.lightmove.api.enrichment.candidate.model.BrightDataPerson;
import app.lightmove.api.enrichment.candidate.model.BrightDataPerson.BrightDataExperience;
import app.lightmove.api.enrichment.candidate.model.BrightDataPerson.BrightDataPosition;
import app.lightmove.api.enrichment.sourcing.constant.TitleLevel;
import app.lightmove.api.enrichment.sourcing.model.SourcingSpec;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The vendor's order is no order at all, so the picks come from the current role's own title. */
class SourcedHitRankingTest {

    private static final SourcingSpec CFO_WORDS = SourcingSpec.of(List.of("Chief", "CFO", "Head", "Director"),
            List.of("Finance", "Financial", "CFO"), List.of(), null);

    @Test
    @DisplayName("the seat nearest the brief's level comes first, the higher of two equally near, then the vendor's order")
    void ranksByLevelAgainstTheSeat() {
        BrightDataPerson manager = person("manager", "Finance Manager");
        BrightDataPerson head = person("head", "Head of Finance");
        BrightDataPerson cfo = person("cfo", "Chief Financial Officer");
        BrightDataPerson svp = person("svp", "SVP Finance");
        BrightDataPerson director = person("director", "Finance Director");

        assertThat(slugs(SourcedHitRanking.ranked(List.of(manager, head, cfo, svp, director), CFO_WORDS,
                Seniority.C_SUITE))).containsExactly("cfo", "svp", "head", "director", "manager");
        assertThat(slugs(SourcedHitRanking.ranked(List.of(manager, cfo, head, svp, director), CFO_WORDS,
                Seniority.N_MINUS_1))).containsExactly("head", "director", "svp", "manager", "cfo");
    }

    @Test
    @DisplayName("a headline quoting a past seat loses to a current role that carries the function")
    void aPastSeatInTheHeadlineIsNotTheRole() {
        BrightDataPerson formerCfo = new BrightDataPerson("former", "former", "Former", null, null,
                "Ex-CFO | Board Advisor", null, null, "AE", null, null, null, true,
                List.of(new BrightDataExperience("DP World", "Chief Strategy Officer", null, null, null, "2024",
                        "Present", null, null)), List.of(), List.of(), List.of());
        BrightDataPerson head = person("head", "Head of Finance");

        assertThat(slugs(SourcedHitRanking.ranked(List.of(formerCfo, head), CFO_WORDS, Seniority.C_SUITE)))
                .containsExactly("head", "former");
    }

    @Test
    @DisplayName("the current title is the open role — inside a grouped company too — and the headline only when none is readable")
    void readsTheCurrentRole() {
        BrightDataPerson grouped = new BrightDataPerson("grouped", "grouped", "Grouped", null, null,
                "Finance leader", null, null, "AE", null, null, null, true,
                List.of(new BrightDataExperience("DP World", null, null, null, null, null, null, null, List.of(
                        new BrightDataPosition("Group CFO", "Jan 2024", "Present"),
                        new BrightDataPosition("Finance Director", "Jan 2020", "Dec 2023")))),
                List.of(), List.of(), List.of());
        BrightDataPerson masked = new BrightDataPerson("masked", "masked", "Masked", null, null,
                "Head of Treasury", null, null, "AE", null, null, null, true,
                List.of(new BrightDataExperience("******", "******* ******", null, null, null, null, null, null,
                        null)), List.of(), List.of(), List.of());

        assertThat(SourcedHitRanking.currentRoleTitle(grouped)).isEqualTo("Group CFO");
        assertThat(SourcedHitRanking.currentRoleTitle(masked)).isEqualTo("Head of Treasury");
    }

    @Test
    @DisplayName("a title's level reads its words, not their fragments, and a deputy sits one level down")
    void readsTheLevel() {
        assertThat(SourcedHitRanking.levelOf("President & CEO")).isEqualTo(TitleLevel.TOP);
        assertThat(SourcedHitRanking.levelOf("Vice President, Finance")).isEqualTo(TitleLevel.HEAD);
        assertThat(SourcedHitRanking.levelOf("Executive Vice President - Group Finance"))
                .isEqualTo(TitleLevel.SENIOR_VICE_PRESIDENT);
        assertThat(SourcedHitRanking.levelOf("Deputy CFO")).isEqualTo(TitleLevel.SENIOR_VICE_PRESIDENT);
        assertThat(SourcedHitRanking.levelOf("Chief of Staff to the CEO")).isEqualTo(TitleLevel.HEAD);
        assertThat(SourcedHitRanking.levelOf("Headquarters Finance Leadership Programme")).isEqualTo(TitleLevel.NONE);
        assertThat(SourcedHitRanking.levelOf("Business Advisor to the Chief Digital Officer")).isEqualTo(TitleLevel.NONE);
        assertThat(SourcedHitRanking.levelOf("Head of Sales to Government")).isEqualTo(TitleLevel.HEAD);
        assertThat(SourcedHitRanking.levelOf("Assistant Manager")).isEqualTo(TitleLevel.NONE);
    }

    @Test
    @DisplayName("a business unit named after the employer is not the job: a head of legal there is no finance seat")
    void readsTheJobWithoutTheEmployer() {
        assertThat(SourcedHitRanking.roleOnly("Head of Legal - DP World Trade Finance - DIFC", "DP World"))
                .isEqualTo("Head of Legal, DIFC");
        assertThat(SourcedHitRanking.roleOnly("Head of Global Sales, DP World Financial Services", "DP World"))
                .isEqualTo("Head of Global Sales");
        assertThat(SourcedHitRanking.roleOnly("Chief Financial Officer at DP World Jeddah", "DP World"))
                .isEqualTo("Chief Financial Officer");
        assertThat(SourcedHitRanking.roleOnly("Chief Financial Officer - Logistics", "DP World"))
                .isEqualTo("Chief Financial Officer, Logistics");
        assertThat(SourcedHitRanking.roleOnly("DP World", "DP World")).isEqualTo("DP World");

        SourcingSpec technology = SourcingSpec.of(List.of("Head"), List.of("Technology", "Digital"), List.of(), null);
        assertThat(slugs(SourcedHitRanking.ranked(List.of(atDpWorld("ops", "Head of Operations"),
                atDpWorld("it", "Head of IT")), technology, Seniority.N_MINUS_1))).containsExactly("it", "ops");

        BrightDataPerson legal = atDpWorld("legal", "Head of Legal - DP World Trade Finance - DIFC");
        BrightDataPerson finance = atDpWorld("finance", "Group Finance Director");
        assertThat(slugs(SourcedHitRanking.ranked(List.of(legal, finance), CFO_WORDS, Seniority.C_SUITE)))
                .containsExactly("finance", "legal");
    }

    private static BrightDataPerson atDpWorld(String slug, String title) {
        return new BrightDataPerson(slug, slug, slug, null, null, title, null, null, "AE", "DP World",
                null, null, true, List.of(new BrightDataExperience("DP World", title, null, null, null, "2020",
                        "Present", null, null)), List.of(), List.of(), List.of());
    }

    private static BrightDataPerson person(String slug, String title) {
        return new BrightDataPerson(slug, slug, slug, null, null, title + " at DP World", null, null, "AE", null,
                null, null, true, List.of(new BrightDataExperience("DP World", title, null, null, null, "2020",
                        "Present", null, null)), List.of(), List.of(), List.of());
    }

    private static List<String> slugs(List<BrightDataPerson> people) {
        return people.stream().map(BrightDataPerson::linkedinId).toList();
    }
}
