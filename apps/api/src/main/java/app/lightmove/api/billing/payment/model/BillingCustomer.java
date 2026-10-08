package app.lightmove.api.billing.payment.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/** A workspace's one Stripe customer (V125), which every webhook names. */
@Entity
@Table(name = "app_lm_billing_customer")
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BillingCustomer {

    @Id
    @Column(name = "workspace_id")
    private UUID workspaceId;

    @Column(name = "stripe_customer_id", nullable = false, length = 64)
    private String stripeCustomerId;
}
