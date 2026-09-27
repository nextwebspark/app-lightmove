package app.lightmove.api.project;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.project.constant.ProjectStage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The stage gate is derived from what a mandate has done, so this matrix is what the gates show. */
class ProjectStageTest {

    @Test
    @DisplayName("a mandate with nothing in its universe is still at the brief")
    void emptyMandateIsAtBrief() {
        assertThat(ProjectStage.reached(ProjectStage.BRIEF, 0, 0, 0)).isEqualTo(ProjectStage.BRIEF);
    }

    @Test
    @DisplayName("one company in the universe opens the universe gate")
    void oneCompanyIsUniverse() {
        assertThat(ProjectStage.reached(ProjectStage.BRIEF, 1, 0, 0)).isEqualTo(ProjectStage.UNIVERSE);
    }

    @Test
    @DisplayName("one executive mapped at a universe company opens the mapping gate")
    void oneMappedCompanyIsMapping() {
        assertThat(ProjectStage.reached(ProjectStage.BRIEF, 3, 1, 0)).isEqualTo(ProjectStage.MAPPING);
    }

    @Test
    @DisplayName("one executive approached opens outreach, wherever they sit")
    void oneReachedOutIsOutreach() {
        assertThat(ProjectStage.reached(ProjectStage.BRIEF, 3, 1, 1)).isEqualTo(ProjectStage.OUTREACH);
        assertThat(ProjectStage.reached(ProjectStage.BRIEF, 0, 0, 1)).isEqualTo(ProjectStage.OUTREACH);
    }

    @Test
    @DisplayName("a stored stage further on than the rows is kept, so a done mandate stays done")
    void recordedStageIsNeverRolledBack() {
        assertThat(ProjectStage.reached(ProjectStage.DELIVERED, 3, 1, 1)).isEqualTo(ProjectStage.DELIVERED);
        assertThat(ProjectStage.reached(ProjectStage.CLOSED, 0, 0, 0)).isEqualTo(ProjectStage.CLOSED);
        assertThat(ProjectStage.reached(ProjectStage.LOCKED, 1, 0, 0)).isEqualTo(ProjectStage.LOCKED);
    }
}
