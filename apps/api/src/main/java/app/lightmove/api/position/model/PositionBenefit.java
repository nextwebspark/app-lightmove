package app.lightmove.api.position.model;

import app.lightmove.api.common.constant.BenefitFrequency;
import app.lightmove.api.position.constant.FieldSource;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One allowance in a package. The amount is nullable on purpose: "annual home leave" with no figure
 * beside it is a real line in a real offer, and storing a zero would assert a number nobody gave us.
 */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PositionBenefit {

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "amount")
    private Long amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "frequency", nullable = false, length = 16)
    private BenefitFrequency frequency;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 16)
    private FieldSource source;

    public static PositionBenefit of(String name, Long amount, BenefitFrequency frequency, FieldSource source) {
        PositionBenefit benefit = new PositionBenefit();
        benefit.name = name.trim();
        benefit.amount = amount;
        benefit.frequency = frequency;
        benefit.source = source;
        return benefit;
    }
}
