package app.lightmove.api.billing.plan.model;

import app.lightmove.api.billing.plan.constant.PlanCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/** One plan of the catalogue (V118), seeded by migration and read-only to the application. */
@Entity
@Table(name = "app_lm_billing_plan")
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BillingPlan {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "code", length = 16)
    private PlanCode code;

    @Column(name = "name", nullable = false, length = 40)
    private String name;

    @Column(name = "seat_price_monthly_fils")
    private Long seatPriceMonthlyFils;

    @Column(name = "seat_price_annual_fils")
    private Long seatPriceAnnualFils;

    @Column(name = "contact_credits_per_seat")
    private Integer contactCreditsPerSeat;

    @Column(name = "custom", nullable = false)
    private boolean custom;

    @Column(name = "stripe_price_monthly_id", length = 64)
    private String stripePriceMonthlyId;

    @Column(name = "stripe_price_annual_id", length = 64)
    private String stripePriceAnnualId;

    @Column(name = "sort_order", nullable = false)
    private short sortOrder;
}
