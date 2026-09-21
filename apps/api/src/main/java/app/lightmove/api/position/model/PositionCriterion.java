package app.lightmove.api.position.model;

import app.lightmove.api.common.constant.CriterionMode;
import app.lightmove.api.position.constant.FieldSource;
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

    /** Where this criterion came from: the template library, a document reading, or typed by hand. */
    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 16)
    private FieldSource source;

    public static PositionCriterion of(String text, CriterionMode mode, FieldSource source) {
        PositionCriterion criterion = new PositionCriterion();
        criterion.text = text.trim();
        criterion.mode = mode;
        criterion.source = source;
        return criterion;
    }

    /** A row a template redraft is free to delete and replace on its next apply. */
    public boolean isTemplateDrafted() {
        return source == FieldSource.TEMPLATE;
    }
}
