package app.lightmove.api.project.model;

import app.lightmove.api.project.constant.StrategySectorKind;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One sector on the strategy — a chip. The label is a plain-text snapshot of a company-universe
 * value (a primary_industry for DIRECT/ADJACENT, an industry_tags entry for INFERRED), never a
 * foreign key. All three kinds share one ordered list on {@link Strategy}; the service splits them by
 * {@link #kind} for the response.
 */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StrategySector {

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 16)
    private StrategySectorKind kind;

    @Column(name = "label", nullable = false, length = 160)
    private String label;

    @Column(name = "selected", nullable = false)
    private boolean selected;

    public static StrategySector of(StrategySectorKind kind, String label, boolean selected) {
        StrategySector sector = new StrategySector();
        sector.kind = kind;
        sector.label = label.trim();
        sector.selected = selected;
        return sector;
    }
}
