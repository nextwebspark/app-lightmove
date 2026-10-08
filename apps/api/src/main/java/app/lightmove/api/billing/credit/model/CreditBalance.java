package app.lightmove.api.billing.credit.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** What a workspace's ledger lines add up to (V121); its row is the lock every ledger write takes first. */
@Entity
@Table(name = "app_lm_credit_balance")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CreditBalance {

    @Id
    @Column(name = "workspace_id", nullable = false, updatable = false)
    private UUID workspaceId;

    @Column(name = "available", nullable = false)
    private long available;

    @Column(name = "held", nullable = false)
    private long held;

    public void apply(CreditEntry entry) {
        available += entry.getAvailableDelta();
        held += entry.getHeldDelta();
    }
}
