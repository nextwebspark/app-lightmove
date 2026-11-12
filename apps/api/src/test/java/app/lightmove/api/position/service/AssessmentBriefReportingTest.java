package app.lightmove.api.position.service;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.position.constant.FieldSource;
import app.lightmove.api.position.model.PositionOrgNode;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AssessmentBriefReportingTest {

    private static final UUID CEO = UUID.randomUUID();
    private static final UUID SEAT = UUID.randomUUID();

    @Test
    @DisplayName("the assessment reads the seat above the mandate's and the seats below it, by title, never by name")
    void theReportingLineIsReadFromTheOrgChart() {
        List<PositionOrgNode> chart = List.of(
                PositionOrgNode.mandateSeat(SEAT, CEO, FieldSource.MANUAL),
                node(CEO, null, "Group CEO", "Rami Haddad"),
                node(UUID.randomUUID(), SEAT, "Head of Treasury", "Lina Saab"),
                node(UUID.randomUUID(), SEAT, null, "Unnamed seat"),
                node(UUID.randomUUID(), CEO, "Chief Operating Officer", null));

        assertThat(PositionService.reportsToOf(chart)).isEqualTo("Group CEO");
        assertThat(PositionService.directReportsOf(chart)).containsExactly("Head of Treasury");
    }

    @Test
    @DisplayName("a chart with no seat above the mandate's, or no chart at all, says nothing of the reporting line")
    void noChartSaysNothing() {
        assertThat(PositionService.reportsToOf(List.of(PositionOrgNode.mandateSeat(SEAT, null, FieldSource.MANUAL))))
                .isNull();
        assertThat(PositionService.reportsToOf(List.of())).isNull();
        assertThat(PositionService.directReportsOf(List.of())).isEmpty();
    }

    private static PositionOrgNode node(UUID id, UUID parent, String title, String name) {
        return PositionOrgNode.of(id, parent, title, name, false, null, null, FieldSource.MANUAL);
    }
}
