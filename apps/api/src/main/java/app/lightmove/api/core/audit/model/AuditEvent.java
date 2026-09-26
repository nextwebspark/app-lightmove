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
 * One immutable line in the security ledger; a database trigger makes it append-only.
 *
 * <p><b>{@code @Immutable} is load-bearing</b>: without it Hibernate can find the {@code jsonb} map
 * dirty after a round trip and flush an {@code UPDATE} that hits the trigger at commit, outside
 * {@code AuditEventWriter}'s try/catch, failing the work it records.
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

    /** The type's {@code code()}: a String so every {@link AuditEventType} enum shares the column. */
    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AuditOutcome outcome;

    /** Null on a failed login: we may not know, or want to assert, who was trying. */
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

    /** Never a credential, token or password, not even hashed: support staff and compliance read this. */
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
