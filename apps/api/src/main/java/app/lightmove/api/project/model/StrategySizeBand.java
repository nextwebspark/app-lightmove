package app.lightmove.api.project.model;

import app.lightmove.api.company.constant.CompanySizeAxis;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One selected company-size band on the strategy — an employee-headcount or revenue band that is in
 * scope. {@link #band} is the {@code EmployeeBand}/{@code RevenueBand} enum <i>name</i>, not its display
 * label or raw range string; {@link #axis} says which catalog it belongs to. Both axes share one ordered
 * list on {@link Strategy}, split by {@link #axis} for the response — the mirror of {@link StrategySector}.
 *
 * <p>Only selected bands are stored: the full catalog is rendered from the enums, so presence here
 * <i>is</i> selection.
 */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StrategySizeBand {

    @Enumerated(EnumType.STRING)
    @Column(name = "axis", nullable = false, length = 16)
    private CompanySizeAxis axis;

    @Column(name = "band", nullable = false, length = 32)
    private String band;

    public static StrategySizeBand of(CompanySizeAxis axis, String band) {
        StrategySizeBand sizeBand = new StrategySizeBand();
        sizeBand.axis = axis;
        sizeBand.band = band;
        return sizeBand;
    }
}
