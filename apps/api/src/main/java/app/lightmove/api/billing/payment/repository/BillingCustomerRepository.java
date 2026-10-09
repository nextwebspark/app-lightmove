package app.lightmove.api.billing.payment.repository;

import app.lightmove.api.billing.payment.model.BillingCustomer;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface BillingCustomerRepository extends JpaRepository<BillingCustomer, UUID> {

    Optional<BillingCustomer> findByStripeCustomerId(String stripeCustomerId);

    /** Of two customers made for one workspace at once, the first kept wins; the other is left unused in Stripe. */
    @Transactional
    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO app_lm_billing_customer (workspace_id, stripe_customer_id) VALUES (:workspaceId, :customerId)
            ON CONFLICT (workspace_id) DO NOTHING""")
    int insertIfAbsent(UUID workspaceId, String customerId);
}
