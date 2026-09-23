package app.lightmove.api.assistant.model;

import app.lightmove.api.core.persistence.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** One answered question in a chat: the steps taken, the company card proposed and what was filed from it. */
@Entity
@Table(name = "app_lm_assistant_turn")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AssistantTurn extends BaseEntity {

    @Column(name = "thread_id", nullable = false, updatable = false)
    private UUID threadId;

    @Column(name = "actor_user_id", nullable = false, updatable = false)
    private UUID actorUserId;

    @Column(name = "workspace_id", nullable = false, updatable = false)
    private UUID workspaceId;

    @Column(name = "question", nullable = false, updatable = false)
    private String question;

    @Column(name = "answer", nullable = false, updatable = false)
    private String answer;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "proposal", updatable = false)
    private AssistantProposal proposal;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "steps", nullable = false, updatable = false)
    private List<AssistantStep> steps = List.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "proposal_accepted")
    private ProposalOutcome proposalAccepted;

    public static AssistantTurn answered(AssistantThread thread, String question, String answer,
                                         List<AssistantStep> steps, AssistantProposal proposal) {
        AssistantTurn turn = new AssistantTurn();
        turn.threadId = thread.getId();
        turn.actorUserId = thread.getUserId();
        turn.workspaceId = thread.getWorkspaceId();
        turn.question = question;
        turn.answer = answer;
        turn.steps = List.copyOf(steps);
        turn.proposal = proposal;
        return turn;
    }

    public void recordAccepted(ProposalOutcome outcome) {
        this.proposalAccepted = outcome;
    }
}
