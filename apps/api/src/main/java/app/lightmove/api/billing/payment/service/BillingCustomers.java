package app.lightmove.api.billing.payment.service;

import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Each workspace's one Stripe customer, and the workspace a webhook's customer belongs to. */
@Component
@RequiredArgsConstructor
public class BillingCustomers {

    private final JdbcTemplate jdbc;

    public Optional<String> customerOf(UUID workspaceId) {
        return jdbc.queryForList("SELECT stripe_customer_id FROM app_lm_billing_customer WHERE workspace_id = ?",
                String.class, workspaceId).stream().findFirst();
    }

    public Optional<UUID> workspaceOf(String customerId) {
        return jdbc.queryForList("SELECT workspace_id FROM app_lm_billing_customer WHERE stripe_customer_id = ?",
                UUID.class, customerId).stream().findFirst();
    }

    /** Of two admins making a customer at once the first kept wins, and the other's is left unused in Stripe. */
    public String record(UUID workspaceId, String customerId) {
        jdbc.update("""
                INSERT INTO app_lm_billing_customer (workspace_id, stripe_customer_id) VALUES (?, ?)
                ON CONFLICT (workspace_id) DO NOTHING""", workspaceId, customerId);
        return customerOf(workspaceId).orElseThrow();
    }
}
