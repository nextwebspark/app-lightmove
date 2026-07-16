package app.lightmove.api.project;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.project.constant.ProjectHealth;
import app.lightmove.api.project.constant.ProjectStage;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Health is derived, so this matrix is the whole contract the UI's dots depend on. */
class ProjectHealthTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 7, 15);

    @Test
    @DisplayName("a delivered or closed mandate is done, whatever its dates say")
    void doneStagesWinOverDates() {
        LocalDate longPast = TODAY.minusYears(1);
        assertThat(ProjectHealth.derive(ProjectStage.DELIVERED, longPast, TODAY))
                .isEqualTo(ProjectHealth.DONE);
        assertThat(ProjectHealth.derive(ProjectStage.CLOSED, longPast, TODAY))
                .isEqualTo(ProjectHealth.DONE);
    }

    @Test
    @DisplayName("a live mandate past its target date is off track")
    void pastTargetIsOff() {
        assertThat(ProjectHealth.derive(ProjectStage.MAPPING, TODAY.minusDays(1), TODAY))
                .isEqualTo(ProjectHealth.OFF);
    }

    @Test
    @DisplayName("a target within 30 days with sourcing still unstarted is at risk")
    void closeTargetBeforeOutreachIsRisk() {
        assertThat(ProjectHealth.derive(ProjectStage.MAPPING, TODAY.plusDays(30), TODAY))
                .isEqualTo(ProjectHealth.RISK);
        // Due today but not past it: still at risk, not off.
        assertThat(ProjectHealth.derive(ProjectStage.BRIEF, TODAY, TODAY))
                .isEqualTo(ProjectHealth.RISK);
    }

    @Test
    @DisplayName("a close target is fine once outreach is live")
    void closeTargetInOutreachIsOk() {
        assertThat(ProjectHealth.derive(ProjectStage.OUTREACH, TODAY.plusDays(10), TODAY))
                .isEqualTo(ProjectHealth.OK);
    }

    @Test
    @DisplayName("a distant target, or none at all, is on track")
    void distantOrAbsentTargetIsOk() {
        assertThat(ProjectHealth.derive(ProjectStage.MAPPING, TODAY.plusDays(31), TODAY))
                .isEqualTo(ProjectHealth.OK);
        assertThat(ProjectHealth.derive(ProjectStage.BRIEF, null, TODAY))
                .isEqualTo(ProjectHealth.OK);
    }
}
