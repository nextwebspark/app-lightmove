package app.lightmove.api.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.project.constant.ProjectType;
import app.lightmove.api.project.model.ProjectTimeline;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The rule the New position modal previews; the SPA's `lib/timeline.ts` must agree with it. */
class ProjectTimelineTest {

    private static final LocalDate START = LocalDate.of(2026, 9, 24);

    @Test
    @DisplayName("a search with no mapping target is given one at 60% of the window, rounded to the day")
    void searchDefaultsMappingTarget() {
        ProjectTimeline timeline = ProjectTimeline.resolve(ProjectType.SEARCH, START, START.plusDays(45), null);
        assertThat(timeline.mappingTargetDate()).isEqualTo(START.plusDays(27));
    }

    @Test
    @DisplayName("a mapping target the consultant chose inside the window is kept")
    void chosenMappingTargetIsKept() {
        ProjectTimeline timeline = ProjectTimeline.resolve(
                ProjectType.SEARCH, START, START.plusDays(45), START.plusDays(45));
        assertThat(timeline.mappingTargetDate()).isEqualTo(START.plusDays(45));
    }

    @Test
    @DisplayName("without both ends of the window there is nothing to default from")
    void openWindowLeavesMappingTargetEmpty() {
        assertThat(ProjectTimeline.resolve(ProjectType.SEARCH, START, null, null).mappingTargetDate()).isNull();
        assertThat(ProjectTimeline.resolve(ProjectType.MAPPING, START, START.plusDays(10), null)
                .mappingTargetDate()).isNull();
    }

    @Test
    @DisplayName("a delivery on the start date, a mapping target before it, or one on a mapping project is refused")
    void outOfOrderDatesAreRefused() {
        assertThatThrownBy(() -> ProjectTimeline.resolve(ProjectType.MAPPING, START, START, null))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> ProjectTimeline.resolve(ProjectType.SEARCH, START, START.plusDays(10), START))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> ProjectTimeline.resolve(
                ProjectType.MAPPING, START, START.plusDays(10), START.plusDays(5)))
                .isInstanceOf(ApiException.class);
    }

    @Test
    @DisplayName("a mapping target is refused unless both ends of the window are there to check it against")
    void mappingTargetNeedsTheWholeWindow() {
        assertThatThrownBy(() -> ProjectTimeline.resolve(
                ProjectType.SEARCH, START, null, LocalDate.of(2020, 1, 1)))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> ProjectTimeline.resolve(
                ProjectType.SEARCH, null, START.plusDays(10), START.plusDays(5)))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> ProjectTimeline.resolve(ProjectType.SEARCH, null, null, START))
                .isInstanceOf(ApiException.class);
    }

    @Test
    @DisplayName("a mapping target after the delivery date is refused")
    void mappingTargetAfterDeliveryIsRefused() {
        assertThatThrownBy(() -> ProjectTimeline.resolve(
                ProjectType.SEARCH, START, START.plusDays(10), START.plusDays(11)))
                .isInstanceOf(ApiException.class);
    }
}
