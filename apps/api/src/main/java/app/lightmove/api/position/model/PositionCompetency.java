package app.lightmove.api.position.model;

import app.lightmove.api.position.constant.CompetencyPanel;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One weighted competency. Both panels live in one ordered list (panel is a field); the service
 * splits by panel for the response and validates the 100% totals only at lock time.
 */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PositionCompetency {

    @Enumerated(EnumType.STRING)
    @Column(name = "panel", nullable = false, length = 16)
    private CompetencyPanel panel;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "weight", nullable = false)
    private int weight;

    public static PositionCompetency of(CompetencyPanel panel, String name, int weight) {
        PositionCompetency row = new PositionCompetency();
        row.panel = panel;
        row.name = name.trim();
        row.weight = weight;
        return row;
    }
}
