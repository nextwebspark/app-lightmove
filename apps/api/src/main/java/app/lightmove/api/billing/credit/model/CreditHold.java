package app.lightmove.api.billing.credit.model;

import app.lightmove.api.billing.credit.constant.CreditAction;
import app.lightmove.api.billing.credit.constant.CreditHoldStatus;
import app.lightmove.api.core.persistence.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Credits reserved for one paid action until it is captured, released or refunded (V118). */
@Entity
@Table(name = "app_lm_credit_hold")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CreditHold extends BaseEntity {

    @Column(name = "workspace_id", nullable = false, updatable = false)
    private UUID workspaceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 32, updatable = false)
    private CreditAction action;

    @Column(name = "credits", nullable = false, updatable = false)
    private long credits;

    /** What the grants covered; {@code credits - covered} is an overdraft, possible only while enforcement is off. */
    @Column(name = "covered", nullable = false, updatable = false)
    private long covered;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private CreditHoldStatus status;

    @Column(name = "idempotency_key", nullable = false, length = 128, updatable = false)
    private String idempotencyKey;

    @Column(name = "user_id", updatable = false)
    private UUID userId;

    @Column(name = "project_id", updatable = false)
    private UUID projectId;

    @Column(name = "person_id", updatable = false)
    private UUID personId;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    public static CreditHold opened(CreditCharge charge, long credits, long covered, Instant expiresAt) {
        CreditHold hold = new CreditHold();
        hold.workspaceId = charge.workspaceId();
        hold.action = charge.action();
        hold.credits = credits;
        hold.covered = covered;
        hold.status = CreditHoldStatus.OPEN;
        hold.idempotencyKey = charge.idempotencyKey();
        hold.userId = charge.userId();
        hold.projectId = charge.projectId();
        hold.personId = charge.personId();
        hold.expiresAt = expiresAt;
        return hold;
    }

    public long overdraft() {
        return credits - covered;
    }

    public CreditReceipt receipt() {
        return new CreditReceipt(getId(), action, credits, covered, status);
    }

    public void settle(CreditHoldStatus from, CreditHoldStatus to) {
        if (status != from) {
            throw new IllegalStateException("hold " + getId() + " is " + status + ", not " + from);
        }
        status = to;
    }
}
