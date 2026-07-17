package app.lightmove.api.project.model;

import app.lightmove.api.project.constant.CriterionMode;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One candidate criterion in a position's ordered list. An owned value, not an entity — the API
 * replaces the whole list, so rows carry no identity beyond their slot.
 */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PositionCriterion {

    @Column(name = "text", nullable = false, length = 300)
    private String text;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode", nullable = false, length = 16)
    private CriterionMode mode;

    /** True when seeded from the brief (today: the template library; later: the AI drafter). */
    @Column(name = "from_brief", nullable = false)
    private boolean fromBrief;

    public static PositionCriterion of(String text, CriterionMode mode, boolean fromBrief) {
        PositionCriterion criterion = new PositionCriterion();
        criterion.text = text.trim();
        criterion.mode = mode;
        criterion.fromBrief = fromBrief;
        return criterion;
    }
}
