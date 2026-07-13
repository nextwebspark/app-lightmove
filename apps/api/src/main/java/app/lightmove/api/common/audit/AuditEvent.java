package app.lightmove.api.common.audit;

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

/**
 * One immutable line in the security ledger.
 *
 * <p>There are no setters and no update path — the row is written once and never touched again. The
 * database enforces the same thing with a trigger, because an audit trail the application can edit
 * proves nothing about the application.
 */
@Entity
@Table(name = "app_lm_audit_event")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt = Instant.now();

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 64)
    private AuditEventType eventType;

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

    AuditEvent(AuditEventType eventType, AuditOutcome outcome, UUID actorUserId, UUID workspaceId,
               String targetType, String targetId, String ipAddress, String userAgent,
               String correlationId, Map<String, Object> metadata) {
        this.occurredAt = Instant.now();
        this.eventType = eventType;
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
