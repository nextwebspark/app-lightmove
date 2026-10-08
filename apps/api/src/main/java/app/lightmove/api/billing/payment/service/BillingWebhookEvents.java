package app.lightmove.api.billing.payment.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** The Stripe events already handled. A claim lives or dies with the transaction handling its event. */
@Component
@RequiredArgsConstructor
public class BillingWebhookEvents {

    private final JdbcTemplate jdbc;

    /** False for an event handled before, or being handled now by another delivery, which this one then waits on. */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean claim(String eventId, String type) {
        return jdbc.update("""
                INSERT INTO app_lm_billing_webhook_event (event_id, type) VALUES (?, ?)
                ON CONFLICT (event_id) DO NOTHING""", eventId, type) == 1;
    }
}
