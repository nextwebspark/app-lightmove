package app.lightmove.api.billing.credit.model;

import app.lightmove.api.billing.credit.constant.CreditGrantSource;
import app.lightmove.api.core.persistence.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** One bucket of contact credits a spend drains (V121); only {@code CreditLedger} changes what remains. */
@Entity
@Table(name = "app_lm_credit_grant")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CreditGrant extends BaseEntity {

    @Column(name = "workspace_id", nullable = false, updatable = false)
    private UUID workspaceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 16, updatable = false)
    private CreditGrantSource source;

    @Column(name = "drain_rank", nullable = false, updatable = false)
    private short drainRank;

    @Column(name = "amount", nullable = false, updatable = false)
    private long amount;

    @Column(name = "remaining", nullable = false)
    private long remaining;

    @Column(name = "effective_at", nullable = false, updatable = false)
    private Instant effectiveAt;

    @Column(name = "expires_at", updatable = false)
    private Instant expiresAt;

    @Column(name = "fils_per_credit", nullable = false, precision = 14, scale = 6, updatable = false)
    private BigDecimal filsPerCredit;

    @Column(name = "external_ref", length = 128, updatable = false)
    private String externalRef;

    @Column(name = "granted_by", updatable = false)
    private UUID grantedBy;

    @Column(name = "note", length = 500, updatable = false)
    private String note;

    public static CreditGrant issued(CreditGrantCommand command, Instant now) {
        CreditGrant grant = new CreditGrant();
        grant.workspaceId = command.workspaceId();
        grant.source = command.source();
        grant.drainRank = (short) command.source().drainRank();
        grant.amount = command.credits();
        grant.remaining = command.credits();
        grant.effectiveAt = command.effectiveAt() == null ? now : command.effectiveAt();
        grant.expiresAt = command.expiresAt();
        grant.filsPerCredit = command.filsPerCredit() == null ? BigDecimal.ZERO : command.filsPerCredit();
        grant.externalRef = command.externalRef();
        grant.grantedBy = command.grantedBy();
        grant.note = command.note();
        return grant;
    }

    public void take(long credits) {
        if (credits > remaining) {
            throw new IllegalStateException("grant " + getId() + " holds " + remaining + ", asked for " + credits);
        }
        remaining -= credits;
    }

    /** @return the credits that lapsed */
    public long expire() {
        long lapsed = remaining;
        remaining = 0;
        return lapsed;
    }

    public void giveBack(long credits) {
        if (remaining + credits > amount) {
            throw new IllegalStateException("grant " + getId() + " would exceed its amount");
        }
        remaining += credits;
    }
}
