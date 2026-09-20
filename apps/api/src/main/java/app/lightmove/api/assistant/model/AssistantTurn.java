package app.lightmove.api.assistant.model;

import app.lightmove.api.assistant.constant.AssistantTurnStatus;
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

/**
 * One exchange: the question on the way in, the settled answer on the way out, and what it cost.
 *
 * <p>Also carries the actor and the audit context, because a turn is accepted on a request thread
 * and may finish on a background one where the SecurityContext is empty and Tomcat has already
 * recycled the request. Storing them is not a shortcut past authorisation — the guard beans are
 * still called and still re-read the database — it is what makes calling them possible at all.
 */
@Entity
@Table(name = "app_lm_assistant_turn")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AssistantTurn extends BaseEntity {

    @Column(name = "thread_id", nullable = false, updatable = false)
    private UUID threadId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private AssistantTurnStatus status;

    @Column(name = "question", nullable = false, updatable = false)
    private String question;

    @Column(name = "answer")
    private String answer;

    /**
     * An {@code ErrorCode} name, stored as text rather than mapped as an enum: the column carries no
     * CHECK, and the house rule is that a constrained column is {@code @Enumerated} while an
     * unconstrained one is a String written through a typed setter ({@code AuditEvent.eventType}).
     */
    @Column(name = "error_code", length = 64)
    private String errorCode;

    @Column(name = "actor_user_id", nullable = false, updatable = false)
    private UUID actorUserId;

    @Column(name = "workspace_id", nullable = false, updatable = false)
    private UUID workspaceId;

    @Column(name = "ip_address", length = 45, updatable = false)
    private String ipAddress;

    @Column(name = "user_agent", length = 512, updatable = false)
    private String userAgent;

    @Column(name = "correlation_id", length = 64, updatable = false)
    private String correlationId;

    @Column(name = "model", length = 64)
    private String model;

    @Column(name = "input_tokens")
    private Integer inputTokens;

    @Column(name = "output_tokens")
    private Integer outputTokens;

    /**
     * Set here and never left to the column's {@code DEFAULT now()}. Hibernate sends an explicit
     * null for an unset field, which the NOT NULL rejects before the default is ever consulted —
     * and {@code ddl-auto: validate} cannot see that coming.
     */
    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt = Instant.now();

    @Column(name = "finished_at")
    private Instant finishedAt;

    public static AssistantTurn running(UUID threadId, UUID actorUserId, UUID workspaceId, String question,
                                        String ipAddress, String userAgent, String correlationId) {
        AssistantTurn turn = new AssistantTurn();
        turn.threadId = threadId;
        turn.actorUserId = actorUserId;
        turn.workspaceId = workspaceId;
        turn.question = question;
        turn.ipAddress = ipAddress;
        turn.userAgent = userAgent;
        turn.correlationId = correlationId;
        turn.status = AssistantTurnStatus.RUNNING;
        return turn;
    }

    /**
     * Ends the turn. Status and {@code finishedAt} move together and only here, because V65's
     * {@code app_lm_assistant_turn_finished_chk} ties them and the touch trigger makes every UPDATE
     * re-evaluate it — setting one without the other is a constraint violation, not a stale field.
     */
    public void succeed(String answerText, String servingModel, Integer inputTokens, Integer outputTokens) {
        this.status = AssistantTurnStatus.SUCCEEDED;
        this.answer = answerText;
        this.model = servingModel;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.errorCode = null;
        this.finishedAt = Instant.now();
    }

    /** The question stays. A turn nobody could answer is still a turn that was asked. */
    public void fail(String code) {
        this.status = AssistantTurnStatus.FAILED;
        this.errorCode = code;
        this.finishedAt = Instant.now();
    }

    public void cancel() {
        this.status = AssistantTurnStatus.CANCELLED;
        this.finishedAt = Instant.now();
    }
}
