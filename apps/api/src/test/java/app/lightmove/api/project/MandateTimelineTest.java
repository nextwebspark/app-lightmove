package app.lightmove.api.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.project.constant.ProjectType;
import app.lightmove.api.project.model.MandateTimeline;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The timeline rules, which both the create and the patch come through — so this matrix is what a
 * mandate's dates can ever be, however they were written.
 */
class MandateTimelineTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 21);

    @Test
    @DisplayName("a mandate that states no start date starts today")
    void startDefaultsToToday() {
        MandateTimeline timeline = MandateTimeline.requested(
                ProjectType.MAPPING, null, TODAY.plusDays(55), null, TODAY);
        assertThat(timeline.startDate()).isEqualTo(TODAY);
    }

    @Test
    @DisplayName("a search derives its mapping target at 60% of the window to the shortlist")
    void mappingTargetIsSixtyPercentOfTheWindow() {
        LocalDate shortlist = TODAY.plusDays(100);
        MandateTimeline timeline = MandateTimeline.requested(
                ProjectType.EXECUTIVE_SEARCH, TODAY, null, shortlist, TODAY);
        assertThat(timeline.mappingTarget()).isEqualTo(TODAY.plusDays(60));
    }

    @Test
    @DisplayName("the derived mapping target rounds half up, as the modal's preview does")
    void derivedTargetRoundsHalfUp() {
        // 41 days × 0.6 = 24.6, which is the 25th day on both sides of the wire.
        assertThat(MandateTimeline.autoMappingTarget(TODAY, TODAY.plusDays(41)))
                .isEqualTo(TODAY.plusDays(25));
    }

    @Test
    @DisplayName("a mapping target the caller stated is kept rather than derived")
    void statedMappingTargetWins() {
        MandateTimeline timeline = MandateTimeline.requested(ProjectType.EXECUTIVE_SEARCH, TODAY,
                TODAY.plusDays(30), TODAY.plusDays(100), TODAY);
        assertThat(timeline.mappingTarget()).isEqualTo(TODAY.plusDays(30));
    }

    @Test
    @DisplayName("a search must say when its shortlist is due")
    void searchNeedsAShortlistDate() {
        assertThatThrownBy(() -> MandateTimeline.requested(
                ProjectType.EXECUTIVE_SEARCH, TODAY, TODAY.plusDays(30), null, TODAY))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("shortlist");
    }

    @Test
    @DisplayName("turning a search into a mapping mandate drops the shortlist it no longer owes")
    void mappingMandateClearsTheShortlist() {
        MandateTimeline timeline = MandateTimeline.requested(ProjectType.MAPPING, TODAY,
                TODAY.plusDays(30), TODAY.plusDays(100), TODAY);
        assertThat(timeline.shortlistTarget()).isNull();
    }

    @Test
    @DisplayName("a milestone cannot fall before the mandate starts")
    void milestonesCannotPrecedeTheStart() {
        assertThatThrownBy(() -> MandateTimeline.requested(
                ProjectType.MAPPING, TODAY, TODAY.minusDays(1), null, TODAY))
                .isInstanceOf(ApiException.class);
    }

    @Test
    @DisplayName("a shortlist before the start is refused on the shortlist, not on the date it derived")
    void shortlistBeforeStartNamesItsOwnField() {
        // The derived mapping target would also land before the start, so the order of the checks is
        // what decides which field the consultant is sent to.
        assertThatThrownBy(() -> MandateTimeline.requested(
                ProjectType.EXECUTIVE_SEARCH, TODAY, null, TODAY.minusDays(10), TODAY))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("shortlist");
    }

    @Test
    @DisplayName("a shortlist cannot be due before the mapping it draws on")
    void shortlistCannotPrecedeMapping() {
        assertThatThrownBy(() -> MandateTimeline.requested(ProjectType.EXECUTIVE_SEARCH, TODAY,
                TODAY.plusDays(60), TODAY.plusDays(30), TODAY))
                .isInstanceOf(ApiException.class);
    }

    @Test
    @DisplayName("a mapping mandate with no map date at all is allowed — nobody has stated one yet")
    void mappingMandateMayHaveNoDates() {
        MandateTimeline timeline =
                MandateTimeline.requested(ProjectType.MAPPING, TODAY, null, null, TODAY);
        assertThat(timeline.mappingTarget()).isNull();
        assertThat(timeline.governingMilestone(false)).isNull();
    }
}
