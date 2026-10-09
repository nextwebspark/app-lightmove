package app.lightmove.api.billing.payment.repository;

import app.lightmove.api.billing.payment.model.BillingWebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

public interface BillingWebhookEventRepository extends JpaRepository<BillingWebhookEvent, String> {

    /**
     * 1 for an event first claimed now, 0 for one handled before or being handled by another delivery, which this
     * call then waits on. The claim lives or dies with the transaction handling the event.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO app_lm_billing_webhook_event (event_id, type) VALUES (:eventId, :type)
            ON CONFLICT (event_id) DO NOTHING""")
    int claim(String eventId, String type);
}
