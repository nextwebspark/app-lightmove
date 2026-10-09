package app.lightmove.api.billing.credit.model;

import app.lightmove.api.billing.credit.constant.CreditAction;
import app.lightmove.api.billing.credit.constant.CreditEntryKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/**
 * One line of the contact-credit ledger (V121), never updated: a trigger refuses it, so {@code @Immutable} keeps
 * Hibernate from ever flushing one.
 */
@Entity
@Table(name = "app_lm_credit_entry")
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CreditEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 16)
    private CreditEntryKind kind;

    @Column(name = "available_delta", nullable = false)
    private long availableDelta;

    @Column(name = "held_delta", nullable = false)
    private long heldDelta;

    @Column(name = "overdraft", nullable = false)
    private long overdraft;

    @Column(name = "grant_id")
    private UUID grantId;

    @Column(name = "hold_id")
    private UUID holdId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", length = 32)
    private CreditAction action;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "project_id")
    private UUID projectId;

    @Column(name = "person_id")
    private UUID personId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static CreditEntry granted(CreditGrant grant, Instant now) {
        CreditEntry entry = new CreditEntry();
        entry.workspaceId = grant.getWorkspaceId();
        entry.kind = CreditEntryKind.GRANT;
        entry.availableDelta = grant.getAmount();
        entry.grantId = grant.getId();
        entry.userId = grant.getGrantedBy();
        entry.createdAt = now;
        return entry;
    }

    public static CreditEntry expired(CreditGrant grant, long credits, Instant now) {
        CreditEntry entry = new CreditEntry();
        entry.workspaceId = grant.getWorkspaceId();
        entry.kind = CreditEntryKind.EXPIRE;
        entry.availableDelta = -credits;
        entry.grantId = grant.getId();
        entry.createdAt = now;
        return entry;
    }

    public static CreditEntry ofHold(CreditHold hold, CreditEntryKind kind, UUID grantId, long availableDelta,
                              long heldDelta, Instant now) {
        CreditEntry entry = new CreditEntry();
        entry.workspaceId = hold.getWorkspaceId();
        entry.kind = kind;
        entry.availableDelta = availableDelta;
        entry.heldDelta = heldDelta;
        entry.grantId = grantId;
        entry.holdId = hold.getId();
        entry.action = hold.getAction();
        entry.userId = hold.getUserId();
        entry.projectId = hold.getProjectId();
        entry.personId = hold.getPersonId();
        entry.createdAt = now;
        return entry;
    }

    public static CreditEntry overdrawn(CreditHold hold, Instant now) {
        CreditEntry entry = ofHold(hold, CreditEntryKind.ADJUST, null, 0, 0, now);
        entry.overdraft = hold.overdraft();
        return entry;
    }
}
