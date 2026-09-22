package app.lightmove.api.project;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.project.constant.MandatePhase;
import app.lightmove.api.project.constant.ProjectType;
import app.lightmove.api.project.model.MandateProgress;
import app.lightmove.api.project.model.MandateTimeline;
import app.lightmove.api.project.model.ProjectProgressCounts;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The figures the projects list and its drawer draw, and the phase the bar is about. */
class MandateProgressTest {

    private static final LocalDate START = LocalDate.of(2026, 9, 1);

    @Test
    @DisplayName("map percent is the share of the live universe anyone has researched")
    void mapPercentCountsResearchedCompanies() {
        MandateProgress progress = mapping(START.plusDays(10), new ProjectProgressCounts(47, 12, 0, 0, 0));
        assertThat(progress.mapPercent()).isEqualTo(26);
        assertThat(progress.activePhase()).isEqualTo(MandatePhase.MAP);
    }

    @Test
    @DisplayName("engage percent is the share of mapped executives anyone has acted on")
    void engagePercentCountsWorkedExecutives() {
        MandateProgress progress = search(START.plusDays(10), new ProjectProgressCounts(4, 4, 12, 3, 2));
        assertThat(progress.engagePercent()).isEqualTo(25);
        assertThat(progress.activePhase()).isEqualTo(MandatePhase.ENGAGE);
        assertThat(progress.completion()).isEqualTo(0.25);
    }

    @Test
    @DisplayName("an empty mandate reads zero rather than dividing by nothing")
    void emptyMandateIsZero() {
        MandateProgress progress = mapping(START, ProjectProgressCounts.NONE);
        assertThat(progress.mapPercent()).isZero();
        assertThat(progress.engagePercent()).isZero();
        assertThat(progress.completion()).isZero();
        assertThat(progress.mappingVelocityPerWeek()).isZero();
    }

    @Test
    @DisplayName("days remaining counts down to the governing milestone and goes negative past it")
    void daysRemainingFollowsTheMilestone() {
        assertThat(mapping(START.plusDays(40), ProjectProgressCounts.NONE).daysRemaining()).isEqualTo(20);
        assertThat(mapping(START.plusDays(70), ProjectProgressCounts.NONE).daysRemaining()).isEqualTo(-10);
    }

    @Test
    @DisplayName("velocity counts the first week as a whole one")
    void velocityDoesNotInflateInTheFirstDays() {
        MandateProgress threeDaysIn = mapping(START.plusDays(3), new ProjectProgressCounts(10, 2, 6, 0, 0));
        assertThat(threeDaysIn.mappingVelocityPerWeek()).isEqualTo(6);

        MandateProgress fourWeeksIn = mapping(START.plusDays(28), new ProjectProgressCounts(10, 2, 6, 0, 0));
        assertThat(fourWeeksIn.mappingVelocityPerWeek()).isEqualTo(1.5);
    }

    @Test
    @DisplayName("the engage clock runs from the mapping target, not from the mandate's start")
    void engageClockStartsWhenMappingEnds() {
        // The map finished exactly on its 60-day target, and a day of the engage window has gone.
        MandateProgress justEngaging =
                search(START.plusDays(61), new ProjectProgressCounts(40, 40, 10, 0, 0));

        assertThat(justEngaging.activePhase()).isEqualTo(MandatePhase.ENGAGE);
        // 1 of the 40 days from the mapping target to the shortlist — not 61 of 100 from the start,
        // which would read as most of the window already spent on work that could not have begun.
        assertThat(justEngaging.elapsedFraction()).isEqualTo(0.025);
    }

    @Test
    @DisplayName("a milestone on the start date is spent the day it arrives, not divided by")
    void zeroLengthWindowDoesNotDivideByZero() {
        MandateTimeline sameDay = new MandateTimeline(ProjectType.MAPPING, START, START, null);
        assertThat(new MandateProgress(sameDay, ProjectProgressCounts.NONE, START).elapsedFraction())
                .isEqualTo(1);
        assertThat(new MandateProgress(sameDay, ProjectProgressCounts.NONE, START.minusDays(1))
                .elapsedFraction()).isZero();
    }

    private static MandateProgress mapping(LocalDate today, ProjectProgressCounts counts) {
        return new MandateProgress(
                new MandateTimeline(ProjectType.MAPPING, START, START.plusDays(60), null), counts, today);
    }

    private static MandateProgress search(LocalDate today, ProjectProgressCounts counts) {
        return new MandateProgress(new MandateTimeline(ProjectType.EXECUTIVE_SEARCH, START,
                START.plusDays(60), START.plusDays(100)), counts, today);
    }
}
