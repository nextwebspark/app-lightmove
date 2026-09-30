package app.lightmove.api.candidate.model;

import app.lightmove.api.candidate.constant.PersonActivityKind;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One line of a person's history: who did what, on which mandate, and when (V91).
 *
 * <p>{@code @Immutable} for {@code AuditEvent}'s reason: a line is written once and never edited, and
 * without it Hibernate can find the {@code jsonb} map dirty after a round trip and flush an update.
 * The mandate's title is kept beside its id so a line still reads after the mandate is deleted.
 */
@Entity
@Table(name = "app_lm_person_activity")
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PersonActivity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workspace_id", nullable = false, updatable = false)
    private UUID workspaceId;

    @Column(name = "person_id", nullable = false, updatable = false)
    private UUID personId;

    @Column(name = "project_id", updatable = false)
    private UUID projectId;

    @Column(name = "project_title", updatable = false)
    private String projectTitle;

    /** Null only where nobody asked for the change and nobody's request stands behind it. */
    @Column(name = "actor_user_id", updatable = false)
    private UUID actorUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 32, updatable = false)
    private PersonActivityKind kind;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "details", columnDefinition = "jsonb", nullable = false, updatable = false)
    private Map<String, Object> details = Map.of();

    public PersonActivity(UUID workspaceId, UUID personId, UUID projectId, String projectTitle,
                          UUID actorUserId, PersonActivityKind kind, Map<String, Object> details) {
        this.workspaceId = workspaceId;
        this.personId = personId;
        this.projectId = projectId;
        this.projectTitle = projectTitle;
        this.actorUserId = actorUserId;
        this.kind = kind;
        this.occurredAt = Instant.now();
        this.details = Map.copyOf(details);
    }
}
