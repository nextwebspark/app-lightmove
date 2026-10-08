package app.lightmove.api.billing.payment.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/** A Stripe event already handled (V122). */
@Entity
@Table(name = "app_lm_billing_webhook_event")
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BillingWebhookEvent {

    @Id
    @Column(name = "event_id")
    private String eventId;

    @Column(name = "type", nullable = false, length = 64)
    private String type;
}
