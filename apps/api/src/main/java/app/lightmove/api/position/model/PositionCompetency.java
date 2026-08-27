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
 * One weighted competency, with the sentence saying what it measures for this mandate. Both panels
 * live in one ordered list — panel is a field — and the service splits by panel for the response.
 *
 * <p>Nothing here enforces a panel totalling 100%: the screen shows that as readiness, and autosave
 * has to be free to persist a panel mid-rebalance.
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

    @Column(name = "description", length = 300)
    private String description;

    @Column(name = "weight", nullable = false)
    private int weight;

    public static PositionCompetency of(CompetencyPanel panel, String name, String description, int weight) {
        PositionCompetency competency = new PositionCompetency();
        competency.panel = panel;
        competency.name = name.trim();
        competency.description = description == null || description.isBlank() ? null : description.trim();
        competency.weight = weight;
        return competency;
    }
}
