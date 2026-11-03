package app.lightmove.api.enrichment.sourcing.service;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.enrichment.candidate.model.BrightDataPerson;
import app.lightmove.api.enrichment.candidate.model.BrightDataPerson.BrightDataExperience;
import app.lightmove.api.enrichment.candidate.model.BrightDataPerson.BrightDataPosition;
import app.lightmove.api.enrichment.sourcing.constant.TitleLevel;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The job held now is what a person is judged by: the open role, less the employer, and the level its title reads at. */
class CurrentRoleTest {

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

        assertThat(CurrentRole.titleOf(grouped)).isEqualTo("Group CFO");
        assertThat(CurrentRole.titleOf(masked)).isEqualTo("Head of Treasury");
    }

    @Test
    @DisplayName("a title's level reads its words, not their fragments, and a deputy sits one level down")
    void readsTheLevel() {
        assertThat(CurrentRole.levelOf("President & CEO")).isEqualTo(TitleLevel.TOP);
        assertThat(CurrentRole.levelOf("Vice President, Finance")).isEqualTo(TitleLevel.HEAD);
        assertThat(CurrentRole.levelOf("Executive Vice President - Group Finance"))
                .isEqualTo(TitleLevel.SENIOR_VICE_PRESIDENT);
        assertThat(CurrentRole.levelOf("Deputy CFO")).isEqualTo(TitleLevel.SENIOR_VICE_PRESIDENT);
        assertThat(CurrentRole.levelOf("Chief of Staff to the CEO")).isEqualTo(TitleLevel.HEAD);
        assertThat(CurrentRole.levelOf("Headquarters Finance Leadership Programme")).isEqualTo(TitleLevel.NONE);
        assertThat(CurrentRole.levelOf("Business Advisor to the Chief Digital Officer")).isEqualTo(TitleLevel.NONE);
        assertThat(CurrentRole.levelOf("Head of Sales to Government")).isEqualTo(TitleLevel.HEAD);
        assertThat(CurrentRole.levelOf("Assistant Manager")).isEqualTo(TitleLevel.NONE);
        assertThat(CurrentRole.levelOf("General Counsel")).isEqualTo(TitleLevel.HEAD);
        assertThat(CurrentRole.levelOf("Deputy General Counsel")).isEqualTo(TitleLevel.MANAGER);
        assertThat(CurrentRole.levelOf("Group Treasurer")).isEqualTo(TitleLevel.HEAD);
    }

    @Test
    @DisplayName("a business unit named after the employer is not the job: a head of legal there is no finance seat")
    void readsTheJobWithoutTheEmployer() {
        assertThat(CurrentRole.roleOnly("Head of Legal - DP World Trade Finance - DIFC", "DP World"))
                .isEqualTo("Head of Legal, DIFC");
        assertThat(CurrentRole.roleOnly("Head of Global Sales, DP World Financial Services", "DP World"))
                .isEqualTo("Head of Global Sales");
        assertThat(CurrentRole.roleOnly("Chief Financial Officer at DP World Jeddah", "DP World"))
                .isEqualTo("Chief Financial Officer");
        assertThat(CurrentRole.roleOnly("Chief Financial Officer - Logistics", "DP World"))
                .isEqualTo("Chief Financial Officer, Logistics");
        assertThat(CurrentRole.roleOnly("DP World", "DP World")).isEqualTo("DP World");
    }
}
