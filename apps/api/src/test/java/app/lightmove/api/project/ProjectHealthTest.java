package app.lightmove.api.project;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.project.constant.ProjectHealth;
import app.lightmove.api.project.constant.ProjectStage;
import app.lightmove.api.project.constant.ProjectType;
import app.lightmove.api.project.model.MandateProgress;
import app.lightmove.api.project.model.MandateTimeline;
import app.lightmove.api.project.model.ProjectProgressCounts;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Health is derived, so this matrix is the whole contract the UI's pills depend on. It is measured
 * against a mandate's own clock: how much of the window to its governing milestone has gone, against
 * how much of the work is done.
 */
class ProjectHealthTest {

    private static final LocalDate START = LocalDate.of(2026, 7, 1);

    /** A hundred-day window, so a day is a point and the thresholds read directly off the fixtures. */
    private static final LocalDate MAPPING_TARGET = START.plusDays(100);

    private static final LocalDate SHORTLIST_TARGET = START.plusDays(200);

    @Test
    @DisplayName("a delivered or closed mandate is done, whatever its dates say")
    void doneStagesWinOverDates() {
        MandateProgress abandoned = mapping(START.plusDays(400), 40, 0);
        assertThat(ProjectHealth.derive(ProjectStage.DELIVERED, abandoned)).isEqualTo(ProjectHealth.DONE);
        assertThat(ProjectHealth.derive(ProjectStage.CLOSED, abandoned)).isEqualTo(ProjectHealth.DONE);
    }

    @Test
    @DisplayName("a mandate nobody has given a milestone is on track")
    void noMilestoneIsOk() {
        MandateTimeline undated = new MandateTimeline(ProjectType.MAPPING, START, null, null);
        MandateProgress progress = new MandateProgress(undated,
                new ProjectProgressCounts(40, 0, 0, 0, 0), START.plusDays(90));
        assertThat(ProjectHealth.derive(ProjectStage.BRIEF, progress)).isEqualTo(ProjectHealth.OK);
    }

    @Test
    @DisplayName("a milestone that has gone by with work outstanding is off track")
    void milestonePassedWithWorkLeftIsOff() {
        assertThat(ProjectHealth.derive(ProjectStage.MAPPING, mapping(MAPPING_TARGET.plusDays(1), 40, 39)))
                .isEqualTo(ProjectHealth.OFF);
    }

    @Test
    @DisplayName("a milestone that has gone by with the work finished is on track")
    void milestonePassedWithWorkDoneIsOk() {
        assertThat(ProjectHealth.derive(ProjectStage.MAPPING, mapping(MAPPING_TARGET.plusDays(1), 40, 40)))
                .isEqualTo(ProjectHealth.OK);
    }

    @Test
    @DisplayName("keeping within fifteen points of the clock is on track")
    void smallDeficitIsOk() {
        // Half the window gone, 40% of the universe researched: 10 points behind.
        assertThat(ProjectHealth.derive(ProjectStage.MAPPING, mapping(START.plusDays(50), 100, 40)))
                .isEqualTo(ProjectHealth.OK);
    }

    @Test
    @DisplayName("between fifteen and thirty points behind the clock is at risk")
    void mediumDeficitIsRisk() {
        assertThat(ProjectHealth.derive(ProjectStage.MAPPING, mapping(START.plusDays(50), 100, 25)))
                .isEqualTo(ProjectHealth.RISK);
    }

    @Test
    @DisplayName("more than thirty points behind the clock is off track")
    void largeDeficitIsOff() {
        assertThat(ProjectHealth.derive(ProjectStage.MAPPING, mapping(START.plusDays(50), 100, 10)))
                .isEqualTo(ProjectHealth.OFF);
    }

    @Test
    @DisplayName("a mandate with nothing done on its first day is on track, not off")
    void dayOneIsOk() {
        assertThat(ProjectHealth.derive(ProjectStage.BRIEF, mapping(START.plusDays(1), 40, 0)))
                .isEqualTo(ProjectHealth.OK);
    }

    @Test
    @DisplayName("a short window cannot read off track inside the settling-in week")
    void settlingInWeekCapsAtRisk() {
        MandateTimeline tenDays =
                new MandateTimeline(ProjectType.MAPPING, START, START.plusDays(10), null);
        MandateProgress nothingDoneOnDayThree = new MandateProgress(tenDays,
                new ProjectProgressCounts(40, 0, 0, 0, 0), START.plusDays(3));
        assertThat(ProjectHealth.derive(ProjectStage.BRIEF, nothingDoneOnDayThree))
                .isEqualTo(ProjectHealth.RISK);
    }

    @Test
    @DisplayName("an empty universe is not a mapped one, so a search stays on its mapping target")
    void emptyUniverseIsNotComplete() {
        MandateProgress nothingTriaged = new MandateProgress(search(),
                new ProjectProgressCounts(0, 0, 0, 0, 0), START.plusDays(150));
        assertThat(nothingTriaged.mappingComplete()).isFalse();
        assertThat(nothingTriaged.governingMilestone()).isEqualTo(MAPPING_TARGET);
        // The mapping target is long gone and nothing is mapped.
        assertThat(ProjectHealth.derive(ProjectStage.BRIEF, nothingTriaged)).isEqualTo(ProjectHealth.OFF);
    }

    @Test
    @DisplayName("a search re-anchors to its shortlist date once the map is complete")
    void searchMovesToShortlistTarget() {
        // Every company researched, half the people worked, and the mapping target already behind us.
        MandateProgress engaging = new MandateProgress(search(),
                new ProjectProgressCounts(40, 40, 20, 10, 4), START.plusDays(110));
        assertThat(engaging.governingMilestone()).isEqualTo(SHORTLIST_TARGET);
        // 55% of the window to the shortlist spent, 50% of the people worked: comfortably on track.
        assertThat(ProjectHealth.derive(ProjectStage.BRIEF, engaging)).isEqualTo(ProjectHealth.OK);
    }

    @Test
    @DisplayName("a mapping-only mandate stays on its map date however complete it is")
    void mappingMandateNeverMoves() {
        MandateProgress finished = mapping(START.plusDays(50), 40, 40);
        assertThat(finished.mappingComplete()).isTrue();
        assertThat(finished.governingMilestone()).isEqualTo(MAPPING_TARGET);
    }

    private static MandateProgress mapping(LocalDate today, long universe, long researched) {
        return new MandateProgress(
                new MandateTimeline(ProjectType.MAPPING, START, MAPPING_TARGET, null),
                new ProjectProgressCounts(universe, researched, 0, 0, 0), today);
    }

    private static MandateTimeline search() {
        return new MandateTimeline(ProjectType.EXECUTIVE_SEARCH, START, MAPPING_TARGET, SHORTLIST_TARGET);
    }
}
