package app.lightmove.api.position.model;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.position.constant.FieldSource;
import app.lightmove.api.position.constant.MandateReason;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PositionTest {

    private static final UUID CEO = UUID.randomUUID();
    private static final UUID SEAT = UUID.randomUUID();

    @Test
    @DisplayName("the seat above the mandate's and the seats below it are read by title, never by name")
    void theReportingLineIsReadFromTheOrgChart() {
        Position position = withChart(List.of(
                PositionOrgNode.mandateSeat(SEAT, CEO, FieldSource.MANUAL),
                node(CEO, null, "Group CEO", "Rami Haddad"),
                node(UUID.randomUUID(), SEAT, "Head of Treasury", "Lina Saab"),
                node(UUID.randomUUID(), SEAT, null, "Unnamed seat"),
                node(UUID.randomUUID(), CEO, "Chief Operating Officer", null)));

        assertThat(position.reportsToTitle()).contains("Group CEO");
        assertThat(position.directReportTitles()).containsExactly("Head of Treasury");
    }

    @Test
    @DisplayName("a chart with no seat above the mandate's, or no chart at all, says nothing of the reporting line")
    void noChartSaysNothing() {
        assertThat(withChart(List.of(PositionOrgNode.mandateSeat(SEAT, null, FieldSource.MANUAL))).reportsToTitle())
                .isEmpty();
        Position blank = withChart(List.of());
        assertThat(blank.reportsToTitle()).isEmpty();
        assertThat(blank.directReportTitles()).isEmpty();
    }

    @Test
    @DisplayName("the reason for hire counts only once someone chose it or the client's document said it")
    void onlyAStatedReasonForHireCounts() {
        Position untouched = Position.forProject(UUID.randomUUID(), null, "AED");
        assertThat(untouched.statedMandateReason()).isEmpty();

        assertThat(withReason(FieldSource.MANUAL).statedMandateReason()).contains(MandateReason.SUCCESSION);
        assertThat(withReason(FieldSource.DOCUMENT).statedMandateReason()).contains(MandateReason.SUCCESSION);
        assertThat(withReason(FieldSource.TEMPLATE).statedMandateReason()).isEmpty();
    }

    private static Position withReason(FieldSource source) {
        Position position = Position.forProject(UUID.randomUUID(), null, "AED");
        position.applyContext(new MandateContext(MandateReason.SUCCESSION, null, List.of(), false, null,
                Map.of("mandateReason", source)));
        return position;
    }

    private static Position withChart(List<PositionOrgNode> chart) {
        Position position = Position.forProject(UUID.randomUUID(), null, "AED");
        position.applyReporting(new ReportingStructure(chart, null, null, null, Map.of()));
        return position;
    }

    private static PositionOrgNode node(UUID id, UUID parent, String title, String name) {
        return PositionOrgNode.of(id, parent, title, name, false, null, null, FieldSource.MANUAL);
    }
}
