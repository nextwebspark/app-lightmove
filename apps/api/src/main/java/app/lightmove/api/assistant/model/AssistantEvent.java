package app.lightmove.api.assistant.model;

import app.lightmove.api.assistant.constant.AssistantEventKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One entry in a running turn's log, and the unit an SSE stream replays.
 *
 * <p>Deliberately not a {@code BaseEntity}: the table has no {@code version}, {@code created_at} or
 * {@code updated_at}, and under {@code ddl-auto: validate} that mismatch fails the whole test
 * context. It follows {@code AuditEvent} instead — a {@code bigserial} id for a row that is high
 * volume, always read in order and never referenced.
 *
 * <p><b>{@code @Immutable} is load-bearing, not decoration.</b> V65 puts a {@code BEFORE UPDATE}
 * trigger on this table that raises {@code insufficient_privilege}, and because {@code seq} is the
 * replay cursor the reason is not one corrupt row — an event edited in place silently reorders the
 * conversation for every reader still catching up. The annotation stops Hibernate dirty-checking
 * these rows at all, so no accidental flush can reach that trigger; if one did, the exception would
 * poison whichever transaction happened to be open rather than merely failing a statement.
 * {@code GeocodedPlace} is the existing precedent. Inserts are unaffected, so it composes with
 * {@code IDENTITY}.
 */
@Entity
@Table(name = "app_lm_assistant_event")
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AssistantEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "turn_id", nullable = false, updatable = false)
    private UUID turnId;

    /**
     * Monotonic within a turn and allocated by the writer. The cursor a reconnecting browser sends
     * back as {@code afterSeq}, which is why it must never be rewritten.
     */
    @Column(name = "seq", nullable = false, updatable = false)
    private int seq;

    /**
     * The {@link AssistantEventKind#wire()} form, stored as text rather than mapped as an enum: the
     * column carries no CHECK, and the house rule is that a constrained column is
     * {@code @Enumerated} while an unconstrained one is a String written through a typed factory
     * ({@code AuditEvent.eventType}). Storing the wire form is also what lets the stream hand it to
     * the browser without parsing it back — see {@link AssistantEventKind}.
     */
    @Column(name = "kind", nullable = false, updatable = false, length = 32)
    private String kind;

    /**
     * Whatever this kind carries. An open {@code Map} rather than this codebase's usual typed jsonb
     * record, because the vocabulary is heterogeneous by design — a question, a chunk of text, a
     * tool's arguments, an error code — so a typed body would have to be a union. {@code AuditEvent}
     * maps its {@code metadata} the same way for the same reason.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, updatable = false)
    private Map<String, Object> payload = Map.of();

    /**
     * Set here and never left to the column's {@code DEFAULT now()}: Hibernate sends an explicit
     * null for an unset field, which the NOT NULL rejects before the default is consulted — the trap
     * already documented on {@code AssistantTurn.startedAt}, and one {@code validate} cannot see.
     */
    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt = Instant.now();

    public static AssistantEvent of(UUID turnId, int seq, AssistantEventKind kind,
                                    Map<String, Object> payload) {
        AssistantEvent event = new AssistantEvent();
        event.turnId = turnId;
        event.seq = seq;
        event.kind = kind.wire();
        // Not Map.copyOf: it rejects null VALUES, and a null is meaningful in a payload (it
        // serialises to JSON null). AuditService.record() shipped exactly that bug — detail() stored
        // a null happily and Map.copyOf threw at commit, out of a request whose work had already
        // succeeded. A LinkedHashMap copy is defensive, ordered and null-tolerant.
        event.payload = payload == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(payload));
        return event;
    }
}
