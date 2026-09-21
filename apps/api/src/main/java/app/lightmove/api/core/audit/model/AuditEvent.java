package app.lightmove.api.core.audit.model;
import app.lightmove.api.core.audit.constant.AuditEventType;
import app.lightmove.api.core.audit.constant.AuditOutcome;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/**
 * One immutable line in the security ledger.
 *
 * <p>There are no setters and no update path — the row is written once and never touched again. The
 * database enforces the same thing with a trigger, because an audit trail the application can edit
 * proves nothing about the application.
 *
 * <p><b>{@code @Immutable} is load-bearing, and it was missing.</b> Having no setters stops the
 * application changing a row; it does not stop Hibernate deciding one is dirty and flushing an
 * {@code UPDATE} of its own accord, which {@code metadata} makes possible — a {@code jsonb} map is
 * compared through a round trip, and what comes back is not always the object that went in. That
 * {@code UPDATE} hits the append-only trigger at commit, long after {@code AuditEventWriter} has
 * returned and outside the try/catch that exists so a lost audit row never fails the work it
 * records. The annotation removes the possibility rather than the symptom: nothing dirty-checks
 * these rows at all. {@code AssistantEvent} and {@code GeocodedPlace} carry it for the same reason;
 * inserts are unaffected, so it composes with {@code IDENTITY}.
 */
@Entity
@Table(name = "app_lm_audit_event")
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt = Instant.now();

    /**
     * The stored form of the event type — its {@code code()}. The column is a plain {@code String}
     * so any of the {@link AuditEventType} feature enums can land in the one column, but the
     * constructor only accepts an {@code AuditEventType}, so a typo can never reach this field.
     */
    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AuditOutcome outcome;

    /** Null on a failed login: we may not know — or may not want to assert — who was trying. */
    @Column(name = "actor_user_id")
    private UUID actorUserId;

    @Column(name = "workspace_id")
    private UUID workspaceId;

    @Column(name = "target_type", length = 64)
    private String targetType;

    @Column(name = "target_id", length = 128)
    private String targetId;

    @Column(name = "ip_address", columnDefinition = "inet")
    private String ipAddress;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    @Column(name = "correlation_id", length = 64)
    private String correlationId;

    /**
     * Context: why a login failed, which address was invited, and so on.
     *
     * <p>Never a credential, a token, or a password — not even a hashed one. This column is read by
     * support staff and exported to compliance reviewers, and it is the easiest place in the system
     * to accidentally spill a secret.
     */
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> metadata = Map.of();

    public AuditEvent(AuditEventType eventType, AuditOutcome outcome, UUID actorUserId, UUID workspaceId,
                      String targetType, String targetId, String ipAddress, String userAgent,
                      String correlationId, Map<String, Object> metadata) {
        this.occurredAt = Instant.now();
        this.eventType = eventType.code();
        this.outcome = outcome;
        this.actorUserId = actorUserId;
        this.workspaceId = workspaceId;
        this.targetType = targetType;
        this.targetId = targetId;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.correlationId = correlationId;
        this.metadata = metadata == null ? Map.of() : metadata;
    }
}
